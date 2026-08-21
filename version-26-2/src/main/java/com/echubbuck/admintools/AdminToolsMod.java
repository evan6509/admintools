package com.echubbuck.admintools;

import com.echubbuck.admintools.common.*;
import com.echubbuck.admintools.common.heuristic.HeuristicEngine;
import com.echubbuck.admintools.commands.*;
import com.echubbuck.admintools.gui.*;
import com.echubbuck.admintools.heuristic.HeuristicTracker;
import com.echubbuck.admintools.identity.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class AdminToolsMod implements ModInitializer {
    public static final String MOD_ID = "admintools";

    private static PermissionManager permissionManager;
    private static ActionLogger actionLogger;
    private static ConfigManager configManager;
    private static HeuristicEngine heuristicEngine;
    private static HeuristicTracker heuristicTracker;

    private static ItemLedger itemLedger;
    private static ItemIdentityManager itemIdentityManager;
    private static ItemEventSink itemEventSink;
    private static ItemDuplicateDetector itemDuplicateDetector;
    private static ItemMovementTracker itemMovementTracker;

    @Override
    public void onInitialize() {
        permissionManager = new PermissionManager();
        actionLogger = new ActionLogger();
        configManager = new ConfigManager();
        heuristicEngine = new HeuristicEngine();
        heuristicTracker = new HeuristicTracker(heuristicEngine);

        // Force data-component registration (static initializer of ItemUidComponent).
        var uidType = ItemUidComponent.TYPE;

        itemLedger = new ItemLedger();
        itemLedger.setMaxEntries(configManager.getInt("ledger_max_entries", 5000));
        itemIdentityManager = new ItemIdentityManager(itemLedger);
        itemDuplicateDetector = new ItemDuplicateDetector(itemIdentityManager);
        itemDuplicateDetector.setDetectCreative(configManager.getBoolean("detect_creative_duplicates", false));
        itemMovementTracker = new ItemMovementTracker(itemIdentityManager, itemDuplicateDetector);
        itemEventSink = new ItemEventSink(itemIdentityManager, itemMovementTracker);

        registerCommands();
        registerEvents();
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            InvSeeCommand.register(dispatcher);
            EnderSeeCommand.register(dispatcher);
            XrayAuditCommand.register(dispatcher);
            AdminRoleCommand.register(dispatcher);
            ItemTraceCommand.register(dispatcher);
            AdminItemCommand.register(dispatcher, context);
        });
    }

    private void registerEvents() {
        ServerTickEvents.START_SERVER_TICK.register(itemMovementTracker::onServerTick);

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player != null) {
                // Mark before drops are spawned; AFTER is too late for some blocks.
                itemEventSink.onBreak(player);
            }
            return true;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            if (player != null) {
                itemMovementTracker.baselinePlayer(player);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.getPlayer();
            if (player != null) {
                itemMovementTracker.forgetPlayer(player.getUUID());
                itemEventSink.forgetPlayer(player.getUUID());
                heuristicEngine.forget(player.getUUID());
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            itemLedger.save();
            itemLedger.close();
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player != null) {
                heuristicTracker.recordBreak(player.getUUID(), state.getBlock());
            }
        });

        PlayerBlockBreakEvents.CANCELED.register((world, player, pos, state, blockEntity) -> {
            if (player != null) {
                itemEventSink.forgetPlayer(player.getUUID());
            }
        });
    }

    public static PermissionManager getPermissionManager() { return permissionManager; }
    public static ActionLogger getActionLogger() { return actionLogger; }
    public static ConfigManager getConfigManager() { return configManager; }
    public static HeuristicEngine getHeuristicEngine() { return heuristicEngine; }
    public static HeuristicTracker getHeuristicTracker() { return heuristicTracker; }

    public static ItemLedger getItemLedger() { return itemLedger; }
    public static ItemIdentityManager getItemIdentityManager() { return itemIdentityManager; }
    public static ItemEventSink getItemEventSink() { return itemEventSink; }
    public static ItemDuplicateDetector getItemDuplicateDetector() { return itemDuplicateDetector; }
    public static ItemMovementTracker getItemMovementTracker() { return itemMovementTracker; }
}
