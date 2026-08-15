package com.echubbuck.admintools;

import com.echubbuck.admintools.common.*;
import com.echubbuck.admintools.common.heuristic.HeuristicEngine;
import com.echubbuck.admintools.commands.*;
import com.echubbuck.admintools.gui.*;
import com.echubbuck.admintools.heuristic.HeuristicTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

public class AdminToolsMod implements ModInitializer {
    public static final String MOD_ID = "admintools";

    private static PermissionManager permissionManager;
    private static ActionLogger actionLogger;
    private static ConfigManager configManager;
    private static HeuristicEngine heuristicEngine;
    private static HeuristicTracker heuristicTracker;

    @Override
    public void onInitialize() {
        permissionManager = new PermissionManager();
        actionLogger = new ActionLogger();
        configManager = new ConfigManager();
        heuristicEngine = new HeuristicEngine();
        heuristicTracker = new HeuristicTracker(heuristicEngine);

        registerCommands();
        registerEvents();
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            InvSeeCommand.register(dispatcher);
            EnderSeeCommand.register(dispatcher);
            XrayAuditCommand.register(dispatcher);
            AdminRoleCommand.register(dispatcher);
        });
    }

    private void registerEvents() {
        ServerTickEvents.START_WORLD_TICK.register(heuristicTracker);

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player != null) {
                heuristicTracker.recordBreak(player.getUUID(), state.getBlock());
            }
        });
    }

    public static PermissionManager getPermissionManager() { return permissionManager; }
    public static ActionLogger getActionLogger() { return actionLogger; }
    public static ConfigManager getConfigManager() { return configManager; }
    public static HeuristicEngine getHeuristicEngine() { return heuristicEngine; }
}
