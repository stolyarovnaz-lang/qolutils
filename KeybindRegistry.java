package com.example.qolutils.input;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Numpad 2-8 (Numpad 0 and 1 are intentionally left alone). Keep NumLock ON. */
public final class KeybindRegistry {
    private static final String CATEGORY = "category.qolutils";

    public static KeyBinding TELEPORT_DEATH;
    public static KeyBinding AUTO_EAT;
    public static KeyBinding AUTO_TORCH;
    public static KeyBinding DROP_JUNK;
    public static KeyBinding AUTO_TOOL;
    public static KeyBinding SAFE_WALK;
    public static KeyBinding PARKOUR;

    private KeybindRegistry() {}

    public static void register() {
        TELEPORT_DEATH = reg("teleport_death", GLFW.GLFW_KEY_KP_2);
        AUTO_EAT       = reg("auto_eat",       GLFW.GLFW_KEY_KP_3);
        AUTO_TORCH     = reg("auto_torch",     GLFW.GLFW_KEY_KP_4);
        DROP_JUNK      = reg("drop_junk",      GLFW.GLFW_KEY_KP_5);
        AUTO_TOOL      = reg("auto_tool",      GLFW.GLFW_KEY_KP_6);
        SAFE_WALK      = reg("safe_walk",      GLFW.GLFW_KEY_KP_7);
        PARKOUR        = reg("parkour",        GLFW.GLFW_KEY_KP_8);
    }

    private static KeyBinding reg(String id, int key) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.qolutils." + id, InputUtil.Type.KEYSYM, key, CATEGORY));
    }
}
