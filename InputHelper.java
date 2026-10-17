package com.example.qolutils.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class InputHelper {
    private InputHelper() {}

    /** True if the physical key/button bound to this KeyBinding is currently held down. */
    public static boolean isPhysicallyDown(KeyBinding binding) {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
        int code = key.getCode();
        if (code == InputUtil.UNKNOWN_KEY.getCode()) return false;
        return switch (key.getCategory()) {
            case KEYSYM -> InputUtil.isKeyPressed(handle, code);
            case MOUSE -> GLFW.glfwGetMouseButton(handle, code) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }

    /** Un-forces a key we pressed virtually, but keeps it down if the player is really holding it. */
    public static void restore(KeyBinding binding) {
        binding.setPressed(isPhysicallyDown(binding));
    }
}
