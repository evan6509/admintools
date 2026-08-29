package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.ActionLogger;
import com.echubbuck.admintools.common.ConfigManager;
import com.echubbuck.admintools.common.ItemLedger;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class PaperAdminToolsPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private PaperPermissionManager permissionManager;
    private ActionLogger actionLogger;
    private ItemLedger itemLedger;
    private InventoryViewer inventoryViewer;
    private PaperItemTracker itemTracker;
    private PaperContainerAuditor containerAuditor;
    private PaperAuditCommands auditCommands;

    @Override
    public void onEnable() {
        configManager = new ConfigManager();
        permissionManager = new PaperPermissionManager();
        actionLogger = new ActionLogger();
        itemLedger = new ItemLedger();
        inventoryViewer = new InventoryViewer(this, actionLogger);
        itemTracker = new PaperItemTracker(this, itemLedger);
        containerAuditor = new PaperContainerAuditor();
        auditCommands = new PaperAuditCommands(this);
        applyConfiguration();

        getServer().getPluginManager().registerEvents(inventoryViewer, this);
        getServer().getPluginManager().registerEvents(containerAuditor, this);
        AdminCommandRouter router = new AdminCommandRouter(this);
        for (String name : List.of("admintools", "invsee", "endersee", "adminaccess",
                "itemtrace", "adminitem", "containertrace")) {
            PluginCommand command = getCommand(name);
            if (command == null) throw new IllegalStateException("Missing command declaration: " + name);
            command.setExecutor(router);
            command.setTabCompleter(router);
        }
        getLogger().info("AdminTools Paper adapter enabled.");
    }

    @Override
    public void onDisable() {
        if (auditCommands != null) auditCommands.close();
        if (containerAuditor != null) containerAuditor.close();
        if (itemTracker != null) itemTracker.close();
        if (itemLedger != null) itemLedger.close();
    }

    public void applyConfiguration() {
        actionLogger.setWriteToFile(configManager.getBoolean("log_actions_to_file", true));
        itemLedger.setMaxEntries(configManager.getInt("ledger_max_entries", 5000));
        if (itemTracker != null) {
            itemTracker.setDetectCreative(configManager.getBoolean("detect_creative_duplicates", false));
        }
        if (auditCommands != null) auditCommands.applyConfiguration();
        if (containerAuditor != null) {
            containerAuditor.setEnabled(configManager.getBoolean("enable_container_audit", true));
        }
    }

    public ConfigManager config() { return configManager; }
    public PaperPermissionManager permissions() { return permissionManager; }
    public ActionLogger actionLogger() { return actionLogger; }
    public ItemLedger ledger() { return itemLedger; }
    public InventoryViewer inventoryViewer() { return inventoryViewer; }
    public PaperItemTracker itemTracker() { return itemTracker; }
    public PaperContainerAuditor containerAuditor() { return containerAuditor; }
    public PaperAuditCommands auditCommands() { return auditCommands; }
}
