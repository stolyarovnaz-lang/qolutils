package com.example.qolutils.feature;

import com.example.qolutils.util.Feedback;
import com.google.common.collect.Multimap;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/** Auto-Tool: hotbar-only, so it needs no inventory packets (just a selected-slot change). */
public final class ToolManager {
    private static boolean enabled;
    private static int restoreSlot = -1;
    private static int lastSet = -1;
    private static int idleTicks;

    private ToolManager() {}

    public static void toggle() {
        enabled = !enabled;
        if (!enabled) reset();
        Feedback.toggle("Auto-Tool", enabled);
    }

    public static void reset() {
        restoreSlot = -1;
        lastSet = -1;
        idleTicks = 0;
    }

    public static void tick(MinecraftClient c) {
        if (!enabled) return;
        ClientPlayerEntity p = c.player;
        PlayerInventory inv = p.getInventory();
        if (c.currentScreen != null || p.isUsingItem()) return;

        HitResult hit = c.crosshairTarget;
        boolean attacking = c.options.attackKey.isPressed();

        if (attacking && hit != null) {
            int best = -1;
            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockState state = c.world.getBlockState(((BlockHitResult) hit).getBlockPos());
                best = bestToolSlot(inv, state);
            } else if (hit.getType() == HitResult.Type.ENTITY
                    && ((EntityHitResult) hit).getEntity() instanceof LivingEntity living) {
                best = bestWeaponSlot(inv, living);
            }
            idleTicks = 0;
            if (best >= 0 && best != inv.selectedSlot) {
                if (restoreSlot < 0) restoreSlot = inv.selectedSlot;
                inv.selectedSlot = best;
                lastSet = best;
            }
        } else if (restoreSlot >= 0 && ++idleTicks > 20) {
            if (inv.selectedSlot == lastSet) inv.selectedSlot = restoreSlot;
            reset();
        }
    }

    // ---- blocks

    private static float toolScore(ItemStack s, BlockState state) {
        if (s.isDamageable() && s.getDamage() >= s.getMaxDamage() - 1) return 0f; // about to break
        float speed = s.getMiningSpeedMultiplier(state);
        if (speed > 1.0f) {
            int eff = EnchantmentHelper.getLevel(Enchantments.EFFICIENCY, s);
            if (eff > 0) speed += eff * eff + 1;
        }
        if (state.isToolRequired() && s.isSuitableFor(state)) speed += 1000f; // required for drops
        return speed;
    }

    private static int bestToolSlot(PlayerInventory inv, BlockState state) {
        float current = toolScore(inv.getStack(inv.selectedSlot), state);
        float best = Math.max(current, 1.0f);
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            float sc = toolScore(inv.getStack(i), state);
            if (sc > best) { best = sc; bestSlot = i; }
        }
        return bestSlot;
    }

    // ---- weapons

    private static double weaponScore(ItemStack s, LivingEntity target) {
        if (s.isDamageable() && s.getDamage() >= s.getMaxDamage() - 1) return 0;
        double dmg = 1.0;
        double spd = 4.0;
        Multimap<EntityAttribute, EntityAttributeModifier> mods = s.getAttributeModifiers(EquipmentSlot.MAINHAND);
        for (EntityAttributeModifier m : mods.get(EntityAttributes.GENERIC_ATTACK_DAMAGE)) dmg += m.getValue();
        for (EntityAttributeModifier m : mods.get(EntityAttributes.GENERIC_ATTACK_SPEED)) spd += m.getValue();
        dmg += EnchantmentHelper.getAttackDamage(s, target.getGroup());
        return dmg * Math.max(spd, 0.1);
    }

    private static int bestWeaponSlot(PlayerInventory inv, LivingEntity target) {
        double best = weaponScore(inv.getStack(inv.selectedSlot), target);
        int bestSlot = -1;
        for (int i = 0; i < 9; i++) {
            double sc = weaponScore(inv.getStack(i), target);
            if (sc > best + 0.01) { best = sc; bestSlot = i; }
        }
        return bestSlot;
    }
}
