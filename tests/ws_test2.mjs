const port=+process.argv[2]; let pass=0, fail=0; const ok=(c,m)=>{ if(c)pass++; else {fail++; console.log('FAIL',m);} };
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const ws=new WebSocket('ws://127.0.0.1:'+port+'/'); const q=[]; ws.onmessage=e=>q.push(JSON.parse(e.data)); await new Promise(r=>ws.onopen=r);
const wait=async(f,ms=4000)=>{ const end=Date.now()+ms; while(Date.now()<end){ const i=q.findIndex(f); if(i>=0) return q.splice(i,1)[0]; await sleep(30);} return null; };
ok(await wait(m=>m.t==='hello'),'hello');
ws.send(JSON.stringify({t:'place',x:0,y:55,z:0,sx:32,sy:21,sz:32})); // before auth -> the connection is dropped
await sleep(300); ok(ws.readyState>=2,'a message before auth drops the connection');
const w2=new WebSocket('ws://127.0.0.1:'+port+'/'); const q2=[]; w2.onmessage=e=>q2.push(JSON.parse(e.data)); await new Promise(r=>w2.onopen=r);
const wait2=async(f,ms=4000)=>{ const end=Date.now()+ms; while(Date.now()<end){ const i=q2.findIndex(f); if(i>=0) return q2.splice(i,1)[0]; await sleep(30);} return null; };
w2.send(JSON.stringify({t:'auth',code:'test-code-1234'})); ok(await wait2(m=>m.t==='auth.ok'),'auth with the correct code (case and dashes do not matter)');
const st=await wait2(m=>m.t==='status'); ok(st&&st.inWorld&&st.dim==='minecraft:overworld'&&!st.active,'status before placement');
w2.send(JSON.stringify({t:'place',x:5000,y:55,z:5000,sx:32,sy:21,sz:32})); const e1=await wait2(m=>m.t==='place.err'); ok(e1&&/far/.test(e1.reason),'placement too far away is rejected: '+(e1&&e1.reason));
w2.send(JSON.stringify({t:'place',x:0,y:55,z:0,sx:999,sy:21,sz:32})); const e2=await wait2(m=>m.t==='place.err'); ok(e2,'placement too large is rejected');
w2.send(JSON.stringify({t:'place',x:0,y:55,z:0,sx:32,sy:21,sz:32,key:'demo'})); ok(await wait2(m=>m.t==='place.ok'),'placement accepted');
const all=[]; const end=Date.now()+2500; while(Date.now()<end){ while(q2.length) all.push(q2.shift()); await sleep(50); }
const txt=JSON.stringify(all);
ok(all.some(m=>m.t==='current.begin')&&all.some(m=>m.t==='current.set')&&all.some(m=>m.t==='current.ground'),'current-state data arrives ('+all.length+' messages)');
ok(!/diamond|chest|spawner|cave_air|oak_planks|stone_bricks/.test(txt),'nothing hidden arrives over the real connection');
w2.send(JSON.stringify({t:'preview',id:7,x:0,y:55,z:0,sx:32,sy:21,sz:32,margin:16})); ok(await wait2(m=>m.t==='pv.done'),'preview is delivered');
w2.send('x'.repeat(20000)); await sleep(300); ok(w2.readyState>=2,'an oversized message drops the connection');
console.log(pass+' passed, '+fail+' failed'); process.exit(fail?1:0);
