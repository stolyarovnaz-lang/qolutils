package com.example.qolutils.feature;

import com.example.qolutils.util.InputHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Auto-Shield. Totem always has priority over the offhand; nothing here prints anything. */
public final class CombatAssistant {
    private static boolean holding;          // we are (virtually) holding the use key
    private static int calmTicks;
    private static int restoreScreenSlot = -1; // where the previous offhand item went
    private static int equipCooldown;

    private CombatAssistant() {}

    public static void reset() {
        if (holding) InputHelper.restore(MinecraftClient.getInstance().options.useKey);
        holding = false;
        calmTicks = 0;
        restoreScreenSlot = -1;
        equipCooldown = 0;
    }

    public static void tick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (equipCooldown > 0) equipCooldown--;
        if (InventoryManager.isEating()) return;

        boolean threat = c.currentScreen == null && detectThreat(c, p);
        if (threat) {
            calmTicks = 0;
            engage(c, p);
        } else {
            calmTicks++;
            if (holding && calmTicks >= 8) release(c);
            if (!holding && restoreScreenSlot >= 0 && calmTicks >= 40) restoreOffhand(c, p);
        }
    }

    private static void engage(MinecraftClient c, ClientPlayerEntity p) {
        if (p.isUsingItem() && !p.getActiveItem().isOf(Items.SHIELD)) return; // bow/food/etc. in use
        if (p.getItemCooldownManager().isCoolingDown(Items.SHIELD)) return;    // disabled by an axe

        Hand hand;
        if (p.getOffHandStack().isOf(Items.SHIELD)) {
            hand = Hand.OFF_HAND;
        } else if (p.getMainHandStack().isOf(Items.SHIELD)) {
            hand = Hand.MAIN_HAND;
        } else {
            if (equipCooldown > 0) return;
            if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return; // never displace the totem
            if (!InventoryManager.canClick(c)) return;
            int idx = InventoryManager.find(p, s -> s.isOf(Items.SHIELD), false);
            if (idx < 0) return;
            restoreScreenSlot = InventoryManager.toScreenSlot(idx);
            InventoryManager.swapWithOffhand(c, restoreScreenSlot);
            equipCooldown = 10;
            hand = Hand.OFF_HAND;
        }

        if (!p.isUsingItem()) {
            // start the block directly so we never right-click a block/entity by accident
            c.interactionManager.interactItem(p, hand);
        }
        if (p.isUsingItem()) {
            c.options.useKey.setPressed(true); // keep vanilla from cancelling the block
            holding = true;
        }
    }

    /** Lets go of the use key; vanilla then lowers the shield by itself. */
    public static void release(MinecraftClient c) {
        if (!holding) return;
        holding = false;
        InputHelper.restore(c.options.useKey);
    }

    private static void restoreOffhand(MinecraftClient c, ClientPlayerEntity p) {
        if (p.isUsingItem() || !InventoryManager.canClick(c)) return;
        if (p.getOffHandStack().isOf(Items.SHIELD)) {
            InventoryManager.swapWithOffhand(c, restoreScreenSlot);
        }
        restoreScreenSlot = -1;
    }

    private static boolean detectThreat(MinecraftClient c, ClientPlayerEntity p) {
        Vec3d center = p.getPos().add(0, p.getHeight() * 0.5, 0);
        Vec3d look = p.getRotationVec(1.0f);
        Box area = p.getBoundingBox().expand(12.0);

        for (Entity e : c.world.getOtherEntities(p, area)) {
            if (e instanceof PersistentProjectileEntity
                    || e instanceof ExplosiveProjectileEntity
                    || e instanceof ShulkerBulletEntity) {
                ProjectileEntity proj = (ProjectileEntity) e;
                if (proj.getOwner() == p) continue;
                Vec3d vel = proj.getVelocity();
                double speed = vel.length();
                if (speed < 0.2) continue;                       // stuck in a block
                Vec3d to = center.subtract(proj.getPos());
                double dist = to.length();
                if (dist < 0.001) return true;
                Vec3d toN = to.multiply(1.0 / dist);
                if (vel.multiply(1.0 / speed).dotProduct(toN) < 0.92) continue; // not aimed at us
                if (dist / speed > 12.0) continue;                              // too far away
                if (look.dotProduct(toN.negate()) > 0) return true;             // coming from the front
            } else if (e instanceof HostileEntity mob && mob.isAlive()) {
                double dist = mob.distanceTo(p);
                if (dist > 3.6) continue;
                if (!mob.isAttacking() && dist > 2.8) continue;
                Vec3d toMob = mob.getPos().subtract(p.getPos());
                if (toMob.lengthSquared() < 1.0E-6) return true;
                if (look.dotProduct(toMob.normalize()) > 0) return true;
            }
        }
        return false;
    }
}
