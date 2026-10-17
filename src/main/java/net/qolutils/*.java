package com.example.qolutils;

import com.example.qolutils.feature.CombatAssistant;
import com.example.qolutils.feature.DeathTracker;
import com.example.qolutils.feature.InventoryManager;
import com.example.qolutils.feature.MovementManager;
import com.example.qolutils.feature.ToolManager;
import com.example.qolutils.input.KeybindRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class QolUtilsClient implements ClientModInitializer {
    public static final String MOD_ID = "qolutils";

    @Override
    public void onInitializeClient() {
        KeybindRegistry.register();

        // Auto-Tool must switch BEFORE vanilla processes attack input this tick.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player != null && client.world != null && client.interactionManager != null
                    && !client.player.isDead()) {
                ToolManager.tick(client);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(QolUtilsClient::onEndTick);
    }

    private static void onEndTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) {
            resetTransientState();
            return;
        }

        DeathTracker.tick(client);
        if (player.isDead()) {
            resetTransientState();
            return;
        }

        handleKeybinds(client);

        InventoryManager.tick(client);   // auto-totem, auto-eat state machine, junk drop queue
        MovementManager.tick(client);    // parkour assistant
        CombatAssistant.tick(client);    // auto-shield
    }

    private static void handleKeybinds(MinecraftClient client) {
        while (KeybindRegistry.TELEPORT_DEATH.wasPressed()) DeathTracker.teleportToDeath(client);
        while (KeybindRegistry.AUTO_EAT.wasPressed())       InventoryManager.startAutoEat(client);
        while (KeybindRegistry.AUTO_TORCH.wasPressed())     InventoryManager.autoTorch(client);
        while (KeybindRegistry.DROP_JUNK.wasPressed())      InventoryManager.queueDropJunk(client);
        while (KeybindRegistry.AUTO_TOOL.wasPressed())      ToolManager.toggle();
        while (KeybindRegistry.SAFE_WALK.wasPressed())      MovementManager.toggleSafeWalk();
        while (KeybindRegistry.PARKOUR.wasPressed())        MovementManager.toggleParkour();
    }

    private static void resetTransientState() {
        InventoryManager.reset();
        MovementManager.reset();
        CombatAssistant.reset();
        ToolManager.reset();
    }
}
