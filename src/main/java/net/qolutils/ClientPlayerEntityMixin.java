package com.example.qolutils.mixin;

import com.example.qolutils.feature.DeathTracker;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Death tracking: the server's health update with 0 HP arrives here while position is still valid. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "updateHealth", at = @At("HEAD"))
    private void qolutils$trackDeath(float health, CallbackInfo ci) {
        if (health <= 0.0f) {
            DeathTracker.record((ClientPlayerEntity) (Object) this);
        }
    }
}
