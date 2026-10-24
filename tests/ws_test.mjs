import net from 'node:net';
const port=+process.argv[2]; let pass=0, fail=0; const ok=(c,m)=>{ if(c)pass++; else {fail++; console.log('FAIL',m);} };
const open=(origin)=>new Promise((res)=>{ const ws=new WebSocket('ws://127.0.0.1:'+port+'/',{headers:origin?{Origin:origin}:undefined}); const q=[]; let waiter=null;
  ws.onmessage=e=>{ const m=JSON.parse(e.data); q.push(m); if(waiter){const w=waiter; waiter=null; w();} };
  const o={ws,q,next:async(t,ms=3000)=>{ const end=Date.now()+ms; for(;;){ const i=q.findIndex(m=>!t||m.t===t); if(i>=0) return q.splice(i,1)[0]; if(Date.now()>end) return null; await new Promise(r=>{ waiter=r; setTimeout(r,50); }); } }, closed:false};
  ws.onclose=()=>{o.closed=true;}; ws.onerror=()=>{o.closed=true; res(o);}; ws.onopen=()=>res(o); });
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
// 1) plain HTTP request -> 426
const raw=await new Promise(res=>{ const s=net.connect(port,'127.0.0.1',()=>s.write('GET / HTTP/1.1\r\nHost: 127.0.0.1:'+port+'\r\n\r\n')); let d=''; s.on('data',x=>d+=x); s.on('close',()=>res(d)); });
ok(/426/.test(raw),'plain HTTP request is rejected');
// 2) foreign origin
const bad=await open('https://evil.example'); await sleep(300); ok(bad.closed&&!bad.q.length,'foreign origin is rejected');
// 3) wrong codes -> lockout
let c=await open(); let h=await c.next('hello'); ok(h&&h.auth,'hello');
for(let i=0;i<5;i++){ c=await open(); await c.next('hello'); c.ws.send(JSON.stringify({t:'auth',code:'WRONG-0000'})); const r=await c.next('auth.err'); ok(r&&r.reason, 'failed attempt '+i); }
c=await open(); await c.next('hello'); c.ws.send(JSON.stringify({t:'auth',code:'TEST-CODE-1234'})); const lk=await c.next('auth.err'); ok(lk&&lk.reason==='locked'&&lk.retryMs>0,'locked after 5 failed attempts (even with the correct code)');
console.log(pass+' passed, '+fail+' failed'); process.exit(fail?1:0);
