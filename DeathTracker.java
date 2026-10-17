package com.example.qolutils.feature;

import com.example.qolutils.util.Feedback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.Locale;

public final class DeathTracker {
    private static boolean hasDeath;
    private static double deathX, deathY, deathZ;
    private static Identifier deathDimension;

    private static boolean recorded;      // already stored for the current "dead" period
    private static boolean pendingNotice; // flash coords on the actionbar after respawn

    private DeathTracker() {}

    /** Called by ClientPlayerEntityMixin (health packet == 0) and by the tick fallback below. */
    public static void record(ClientPlayerEntity player) {
        if (recorded) return;
        recorded = true;
        hasDeath = true;
        deathX = player.getX();
        deathY = player.getY();
        deathZ = player.getZ();
        deathDimension = player.getWorld().getRegistryKey().getValue();
        pendingNotice = true;
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player.isDead() || player.getHealth() <= 0.0f) {
            record(player); // fallback in case the mixin was bypassed
        } else {
            recorded = false;
            if (pendingNotice) {
                pendingNotice = false;
                Feedback.actionBar("Death logged: " + shortCoords());
            }
        }
    }

    public static void teleportToDeath(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (!hasDeath) {
            Feedback.actionBar("No death location recorded");
            return;
        }
        // No cheats / no permission: just flash the coordinates on the actionbar.
        if (!player.hasPermissionLevel(2)) {
            Feedback.actionBar("Last death: " + shortCoords());
            return;
        }
        String coords = String.format(Locale.ROOT, "%.2f %.2f %.2f", deathX, deathY, deathZ);
        Identifier current = client.world.getRegistryKey().getValue();
        String command = current.equals(deathDimension)
                ? "tp @s " + coords
                : "execute in " + deathDimension + " run tp @s " + coords;
        // No leading slash. Does not echo anything into chat on our side.
        player.networkHandler.sendChatCommand(command);
    }

    private static String shortCoords() {
        return String.format(Locale.ROOT, "%d %d %d [%s]",
                (int) Math.floor(deathX), (int) Math.floor(deathY), (int) Math.floor(deathZ),
                deathDimension == null ? "?" : deathDimension.getPath());
    }
}
