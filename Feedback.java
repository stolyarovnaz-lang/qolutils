package com.example.qolutils.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** The ONLY place the mod talks to the player: the actionbar overlay. Never chat. */
public final class Feedback {
    private Feedback() {}

    public static void actionBar(String text) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(Text.literal(text).formatted(Formatting.GRAY), true);
        }
    }

    public static void toggle(String name, boolean on) {
        actionBar(name + ": " + (on ? "ON" : "OFF"));
    }
}
