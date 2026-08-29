package com.echubbuck.admintools.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/** Placeholder boundary populated by the Paper audit services in the next port milestone. */
public final class PaperAuditCommands {
    private final PaperAdminToolsPlugin plugin;

    public PaperAuditCommands(PaperAdminToolsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String command, String[] args) {
        sender.sendMessage(Component.text("The Paper audit service is still initializing.", NamedTextColor.RED));
        return true;
    }

    public List<String> complete(String command, String[] args) {
        return List.of();
    }

    public void applyConfiguration() {}

    public void close() {}
}
