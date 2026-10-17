package com.example.qolutils.mixin;

import com.example.qolutils.feature.MovementManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SafeWalk: vanilla clips movement at ledges only while sneaking; we force that clip. */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
    private void qolutils$safeWalk(CallbackInfoReturnable<Boolean> cir) {
        if (MovementManager.shouldClipAtLedge((PlayerEntity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
