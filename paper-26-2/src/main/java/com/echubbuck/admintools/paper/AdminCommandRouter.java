package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.ConfigManager;
import com.echubbuck.admintools.common.PermissionNodes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Command routing for the Paper adapter. Item and container commands are attached by later services. */
public final class AdminCommandRouter implements CommandExecutor, TabCompleter {
    private final PaperAdminToolsPlugin plugin;

    public AdminCommandRouter(PaperAdminToolsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "invsee" -> invsee(sender, args);
            case "endersee" -> endersee(sender, args);
            case "adminaccess" -> adminAccess(sender, args);
            case "admintools" -> reload(sender, args);
            case "itemtrace", "adminitem", "containertrace" -> plugin.auditCommands().execute(sender, command.getName(), args);
            default -> false;
        };
    }

    private boolean invsee(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) return fail(sender, "This command requires a player.");
        if (!plugin.permissions().canUse(sender, PermissionNodes.INVSEE)) return denied(sender);
        if (args.length < 1 || args.length > 2) return false;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) return fail(sender, "That player is not online.");
        boolean edit = args.length == 2 && args[1].equalsIgnoreCase("edit");
        if (args.length == 2 && !edit) return false;
        if (edit && !plugin.permissions().canUse(sender, PermissionNodes.INVSEE_EDIT)) return denied(sender);
        if (!plugin.config().getBoolean("enable_inventory_viewer", true)) return fail(sender, "Inventory viewer is disabled.");
        plugin.inventoryViewer().openInventory(admin, target,
                edit || plugin.config().getBoolean("invsee_edit_mode", false));
        return true;
    }

    private boolean endersee(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) return fail(sender, "This command requires a player.");
        if (!plugin.permissions().canUse(sender, PermissionNodes.ENDERSEE)) return denied(sender);
        if (args.length != 1) return false;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) return fail(sender, "That player is not online.");
        if (!plugin.config().getBoolean("enable_ender_chest_viewer", true)) return fail(sender, "Ender chest viewer is disabled.");
        plugin.inventoryViewer().openEnderChest(admin, target);
        return true;
    }

    private boolean adminAccess(CommandSender sender, String[] args) {
        if (sender instanceof Player player && !player.isOp()) return denied(sender);
        if (args.length < 2) return false;
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) return fail(sender, "That player is not online.");
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "grant" -> {
                if (args.length != 3 || !PermissionNodes.VALUES.contains(args[2])) return false;
                boolean changed = plugin.permissions().grant(target.getUniqueId(), args[2]);
                return ok(sender, changed ? "Granted " + args[2] + " to " + target.getName() + "."
                        : target.getName() + " already has that grant.");
            }
            case "remove" -> {
                if (args.length != 3) return false;
                boolean changed = plugin.permissions().remove(target.getUniqueId(), args[2]);
                return ok(sender, changed ? "Removed " + args[2] + " from " + target.getName() + "."
                        : target.getName() + " did not have that grant.");
            }
            case "list" -> {
                if (args.length != 2) return false;
                return ok(sender, target.getName() + ": " + plugin.permissions().permissionsFor(target.getUniqueId()));
            }
            default -> { return false; }
        }
    }

    private boolean reload(CommandSender sender, String[] args) {
        if (sender instanceof Player player && !player.isOp()) return denied(sender);
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) return false;
        ConfigManager config = plugin.config();
        boolean configOk = config.load();
        boolean permissionsOk = plugin.permissions().load();
        plugin.applyConfiguration();
        return configOk && permissionsOk
                ? ok(sender, "AdminTools configuration reloaded.")
                : fail(sender, "Reload failed; the previous valid settings remain active.");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        List<String> values = new ArrayList<>();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if ((name.equals("invsee") || name.equals("endersee")) && args.length == 1) {
            Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
        } else if (name.equals("invsee") && args.length == 2) {
            values.add("edit");
        } else if (name.equals("adminaccess") && args.length == 1) {
            values.addAll(List.of("grant", "remove", "list"));
        } else if (name.equals("adminaccess") && args.length == 2) {
            Bukkit.getOnlinePlayers().forEach(player -> values.add(player.getName()));
        } else if (name.equals("adminaccess") && args.length == 3 && args[0].equalsIgnoreCase("grant")) {
            values.addAll(PermissionNodes.VALUES);
        } else if (name.equals("admintools") && args.length == 1) {
            values.add("reload");
        } else {
            values.addAll(plugin.auditCommands().complete(name, args));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        values.removeIf(value -> !value.toLowerCase(Locale.ROOT).startsWith(prefix));
        return values;
    }

    private static boolean denied(CommandSender sender) {
        return fail(sender, "You do not have permission to use that command.");
    }

    private static boolean fail(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
        return true;
    }

    private static boolean ok(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
        return true;
    }
}
