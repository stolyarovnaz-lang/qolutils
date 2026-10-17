package com.example.qolutils.feature;

import com.example.qolutils.util.Feedback;
import com.example.qolutils.util.InputHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class MovementManager {
    private static boolean safeWalk;
    private static boolean parkour;
    private static boolean jumpPulse; // we are virtually holding the jump key for this tick

    private MovementManager() {}

    public static void toggleSafeWalk() {
        safeWalk = !safeWalk;
        Feedback.toggle("SafeWalk", safeWalk);
    }

    public static void toggleParkour() {
        parkour = !parkour;
        Feedback.toggle("Parkour Assist", parkour);
    }

    public static void reset() {
        if (jumpPulse) {
            InputHelper.restore(MinecraftClient.getInstance().options.jumpKey);
            jumpPulse = false;
        }
    }

    /** Called from PlayerEntityMixin#clipAtLedge. */
    public static boolean shouldClipAtLedge(PlayerEntity player) {
        if (!safeWalk || !(player instanceof ClientPlayerEntity)) return false;
        if (player.getAbilities().flying) return false;
        // Holding jump (also our parkour pulse) lets you leave the edge on purpose.
        return !MinecraftClient.getInstance().options.jumpKey.isPressed();
    }

    public static void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;

        // release the one-tick jump pulse from the previous tick
        if (jumpPulse) {
            jumpPulse = false;
            InputHelper.restore(c.options.jumpKey);
        }

        if (!parkour || c.currentScreen != null) return;
        if (!p.isOnGround() || p.isSneaking() || p.isTouchingWater() || p.isInLava()
                || p.hasVehicle() || p.isClimbing() || p.isFallFlying() || p.getAbilities().flying) return;
        if (!c.options.forwardKey.isPressed()) return;

        Vec3d v = p.getVelocity();
        double h2 = v.x * v.x + v.z * v.z;
        if (h2 < 0.0025) return; // no momentum
        double h = Math.sqrt(h2);

        // approximate displacement of the NEXT tick along the current heading
        double step = p.isSprinting() ? 0.28 : 0.22;
        Box probe = p.getBoundingBox().offset(v.x / h * step, -0.5, v.z / h * step);

        // nothing solid within 0.5 below the hitbox at the next position -> we are at the edge: jump now
        if (c.world.isSpaceEmpty(p, probe)) {
            c.options.jumpKey.setPressed(true);
            jumpPulse = true;
        }
    }
}
