package de.buildsync.client.mixin;

import de.buildsync.client.BuildSyncClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reports every successful block change of the client world (server updates pass through here too).
 * require = 0: if the method changes in a future version, the hook is skipped instead of crashing the game –
 * the slow reconciliation in the core then catches everything.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
	@Shadow
	public abstract boolean isClientSide();

	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"), require = 0)
	private void buildsync$onSetBlock(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && this.isClientSide()) {
			BuildSyncClient.onBlockSet((Level) (Object) this, pos.getX(), pos.getY(), pos.getZ());
		}
	}
}
