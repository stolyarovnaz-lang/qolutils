package com.example.qolutils.feature;

import com.example.qolutils.util.InputHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.util.ArrayDeque;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Everything that touches inventory slots. All of it is silent (no chat, no actionbar).
 *
 * Packet safety rules used everywhere:
 *  - only click while the open handler is the PLAYER handler (syncId 0) and the cursor is empty
 *  - always go through interactionManager.clickSlot (it predicts the result locally AND sends a
 *    correct ClickSlotC2SPacket with the changed-slot map / revision, so no desync)
 *  - cooldowns / 1 drop per tick, so we never spam the server
 *  - re-validate the stack right before every click
 *
 * PlayerScreenHandler layout: 0 craft out, 1-4 craft in, 5-8 armor, 9-35 main inv, 36-44 hotbar, 45 offhand.
 * PlayerInventory index:       0-8 hotbar, 9-35 main inv.
 */
public final class InventoryManager {
    private static final int OFFHAND_BUTTON = 40;            // SWAP button 40 == offhand (screen slot 45)
    private static final float TOTEM_HEALTH = 6.0f;          // 3 hearts
    private static final int EAT_TIMEOUT_TICKS = 80;
    private static final Set<Item> JUNK = Set.of(
            Items.DIORITE, Items.ANDESITE, Items.GRANITE, Items.GRAVEL, Items.COBBLESTONE);

    // auto-totem
    private static int totemCooldown;

    // auto-eat
    private enum EatState { IDLE, EATING }
    private static EatState eatState = EatState.IDLE;
    private static Hand eatHand = Hand.MAIN_HAND;
    private static int eatTicks;
    private static int eatRestoreSlot = -1;     // selected hotbar slot to go back to (-1: unchanged)
    private static int eatSwapScreenSlot = -1;  // inventory screen slot we swapped into the hotbar (-1: none)
    private static int eatSwapHotbar = -1;      // hotbar index used for that swap

    // junk dropping
    private static final ArrayDeque<Integer> dropQueue = new ArrayDeque<>();

    private InventoryManager() {}

    public static void tick(MinecraftClient c) {
        tickAutoTotem(c);
        tickEat(c);
        tickDrop(c);
    }

    public static void reset() {
        if (eatState != EatState.IDLE) {
            InputHelper.restore(MinecraftClient.getInstance().options.useKey);
        }
        eatState = EatState.IDLE;
        eatRestoreSlot = eatSwapScreenSlot = eatSwapHotbar = -1;
        dropQueue.clear();
        totemCooldown = 0;
    }

    public static boolean isEating() {
        return eatState != EatState.IDLE;
    }

    // ------------------------------------------------------------------ helpers

    public static boolean canClick(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        return p != null && c.interactionManager != null
                && p.currentScreenHandler == p.playerScreenHandler
                && p.playerScreenHandler.getCursorStack().isEmpty();
    }

    /** PlayerInventory index (0-35) -> PlayerScreenHandler slot id. */
    public static int toScreenSlot(int invIndex) {
        return invIndex < 9 ? 36 + invIndex : invIndex;
    }

    /** Swaps the given screen slot with the offhand (slot 45) in a single, server-validated click. */
    public static void swapWithOffhand(MinecraftClient c, int screenSlot) {
        ClientPlayerEntity p = c.player;
        c.interactionManager.clickSlot(p.playerScreenHandler.syncId, screenSlot, OFFHAND_BUTTON, SlotActionType.SWAP, p);
    }

    /** Finds a PlayerInventory index (0-35) matching the filter, or -1. */
    public static int find(ClientPlayerEntity p, Predicate<ItemStack> filter, boolean mainFirst) {
        PlayerInventory inv = p.getInventory();
        int[][] ranges = mainFirst ? new int[][]{{9, 36}, {0, 9}} : new int[][]{{0, 9}, {9, 36}};
        for (int[] r : ranges) {
            for (int i = r[0]; i < r[1]; i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty() && filter.test(s)) return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ auto-totem

    private static void tickAutoTotem(MinecraftClient c) {
        if (totemCooldown > 0) { totemCooldown--; return; }
        ClientPlayerEntity p = c.player;
        if (p.getHealth() >= TOTEM_HEALTH) return;
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) || p.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        if (!canClick(c)) return;

        int idx = find(p, s -> s.isOf(Items.TOTEM_OF_UNDYING), true);
        if (idx < 0) return;

        swapWithOffhand(c, toScreenSlot(idx));
        totemCooldown = 6; // let the server confirm before we ever try again
    }

    // ------------------------------------------------------------------ auto-eat

    private static float foodScore(ItemStack s) {
        if (s.isEmpty()) return -1f;
        FoodComponent f = s.getItem().getFoodComponent();
        if (f == null) return -1f;
        if (s.isOf(Items.CHORUS_FRUIT)) return -1f;          // teleports you
        if (!f.getStatusEffects().isEmpty()) return -1f;     // rotten flesh, golden apples, stews, ...
        return f.getHunger() * f.getSaturationModifier() * 2.0f; // actual saturation restored
    }

    public static void startAutoEat(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (eatState != EatState.IDLE || c.currentScreen != null || !canClick(c)) return;
        if (!p.canConsume(false)) return; // already full

        PlayerInventory inv = p.getInventory();
        int bestInv = -2; // -2 none, -1 offhand, 0-35 inventory index
        float best = 0f;

        float s = foodScore(p.getOffHandStack());
        if (s > best) { best = s; bestInv = -1; }
        for (int i = 0; i < 36; i++) {
            float sc = foodScore(inv.getStack(i));
            if (sc > best) { best = sc; bestInv = i; }
        }
        if (bestInv == -2) return;

        CombatAssistant.release(c); // eating wins over the shield

        eatRestoreSlot = -1;
        eatSwapScreenSlot = -1;
        eatSwapHotbar = -1;

        if (bestInv == -1) {
            eatHand = Hand.OFF_HAND;
        } else {
            eatHand = Hand.MAIN_HAND;
            if (bestInv < 9) {
                eatRestoreSlot = inv.selectedSlot;
                inv.selectedSlot = bestInv; // synced by interactItem / tick
            } else {
                // never touch the offhand (might hold a totem): swap into the selected hotbar slot instead
                eatSwapHotbar = inv.selectedSlot;
                eatSwapScreenSlot = bestInv;
                c.interactionManager.clickSlot(p.playerScreenHandler.syncId, bestInv, eatSwapHotbar, SlotActionType.SWAP, p);
            }
        }

        c.interactionManager.interactItem(p, eatHand); // starts the use directly (never places blocks)
        c.options.useKey.setPressed(true);             // vanilla cancels item use when the key is up
        eatState = EatState.EATING;
        eatTicks = 0;
    }

    private static void tickEat(MinecraftClient c) {
        if (eatState != EatState.EATING) return;
        ClientPlayerEntity p = c.player;
        eatTicks++;

        boolean finished = (eatTicks > 2 && !p.isUsingItem()) || eatTicks > EAT_TIMEOUT_TICKS;
        if (!finished) {
            c.options.useKey.setPressed(true);
            return;
        }
        InputHelper.restore(c.options.useKey);

        // need the player handler to swap back safely; wait (bounded) if a container is open
        if (eatSwapScreenSlot >= 0 && !canClick(c)) {
            if (eatTicks > EAT_TIMEOUT_TICKS + 200) {
                eatState = EatState.IDLE;
                eatRestoreSlot = eatSwapScreenSlot = eatSwapHotbar = -1;
            }
            return;
        }

        if (eatSwapScreenSlot >= 0) {
            c.interactionManager.clickSlot(p.playerScreenHandler.syncId, eatSwapScreenSlot, eatSwapHotbar, SlotActionType.SWAP, p);
        }
        if (eatRestoreSlot >= 0) {
            p.getInventory().selectedSlot = eatRestoreSlot;
        }
        eatState = EatState.IDLE;
        eatRestoreSlot = eatSwapScreenSlot = eatSwapHotbar = -1;
    }

    // ------------------------------------------------------------------ auto-torch

    public static void autoTorch(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        ClientWorld w = c.world;
        if (c.currentScreen != null || !canClick(c) || eatState != EatState.IDLE || p.isUsingItem()) return;

        BlockPos feet = p.getBlockPos();
        if (w.getLightLevel(LightType.BLOCK, feet) >= 4) return;

        BlockPos floor = feet.down();
        BlockState floorState = w.getBlockState(floor);
        if (!w.getBlockState(feet).isReplaceable()) return;
        if (!Block.sideCoversSmallSquare(w, floor, Direction.UP)) return;
        // don't accidentally open chests / crafting tables instead of placing
        if (floorState.hasBlockEntity() || floorState.createScreenHandlerFactory(w, floor) != null) return;

        int idx = find(p, s -> s.isOf(Items.TORCH), false);
        if (idx < 0) idx = find(p, s -> s.isOf(Items.SOUL_TORCH), false);
        if (idx < 0) return;

        PlayerInventory inv = p.getInventory();
        int prevSel = inv.selectedSlot;
        boolean swapped = false;
        if (idx < 9) {
            inv.selectedSlot = idx;
        } else {
            c.interactionManager.clickSlot(p.playerScreenHandler.syncId, idx, prevSel, SlotActionType.SWAP, p);
            swapped = true;
        }

        Vec3d hitPos = new Vec3d(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, floor, false);
        ActionResult result = c.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        if (result.shouldSwingHand()) p.swingHand(Hand.MAIN_HAND);

        if (swapped) {
            c.interactionManager.clickSlot(p.playerScreenHandler.syncId, idx, prevSel, SlotActionType.SWAP, p);
        } else {
            inv.selectedSlot = prevSel;
        }
    }

    // ------------------------------------------------------------------ drop junk

    public static void queueDropJunk(MinecraftClient c) {
        if (!canClick(c)) return;
        ClientPlayerEntity p = c.player;
        dropQueue.clear();
        for (int i = 0; i < 36; i++) {
            if (JUNK.contains(p.getInventory().getStack(i).getItem())) {
                dropQueue.add(toScreenSlot(i));
            }
        }
    }

    private static void tickDrop(MinecraftClient c) {
        if (dropQueue.isEmpty() || eatState != EatState.IDLE) return;
        if (!canClick(c)) { dropQueue.clear(); return; }
        ClientPlayerEntity p = c.player;
        int slot = dropQueue.poll();
        ItemStack st = p.playerScreenHandler.getSlot(slot).getStack();
        if (!st.isEmpty() && JUNK.contains(st.getItem())) {
            // THROW + button 1 == drop the whole stack. One click per tick -> no packet burst.
            c.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot, 1, SlotActionType.THROW, p);
        }
    }
}
