package com.echubbuck.admintools.paper;

import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemLedgerEntry;
import com.echubbuck.admintools.common.ItemMovementEvent;
import com.echubbuck.admintools.common.PermissionNodes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PaperAuditCommands {
    private static final int PAGE_SIZE = 10;
    private static final SimpleDateFormat TIME = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final PaperAdminToolsPlugin plugin;

    public PaperAuditCommands(PaperAdminToolsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String command, String[] args) {
        return switch (command.toLowerCase(Locale.ROOT)) {
            case "itemtrace" -> itemTrace(sender, args);
            case "adminitem" -> adminItem(sender, args);
            case "containertrace" -> plugin.containerAuditor().trace(sender, args);
            default -> false;
        };
    }

    public List<String> complete(String command, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.equals("adminitem")) {
            if (args.length == 1) out.addAll(List.of("give", "remove"));
            if (args.length == 2) Bukkit.getOnlinePlayers().forEach(player -> out.add(player.getName()));
            if (args.length == 3) {
                for (Material material : Material.values()) if (material.isItem()) out.add(material.getKey().toString());
            }
        } else if (command.equals("containertrace") && args.length == 4) {
            Bukkit.getWorlds().forEach(world -> out.add(world.getName()));
        }
        return out;
    }

    public void applyConfiguration() {}

    public void close() {}

    private boolean itemTrace(CommandSender sender, String[] args) {
        if (!plugin.permissions().canUse(sender, PermissionNodes.ITEMTRACE)) return denied(sender);
        if (args.length < 1 || args.length > 2) return false;
        String uid;
        int page = 1;
        try {
            uid = UUID.fromString(args[0]).toString();
            if (args.length == 2) page = Math.max(1, Integer.parseInt(args[1]));
        } catch (IllegalArgumentException exception) {
            return fail(sender, "Invalid UUID or page number.");
        }
        ItemLedgerEntry entry = plugin.ledger().entry(uid);
        if (entry == null) return fail(sender, "No identity found for " + uid + ".");
        sender.sendMessage(Component.text("--- Item Trace: " + uid + " ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Item: " + entry.itemId + " (" + entry.count + ")", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Created: " + TIME.format(new Date(entry.creationTime))
                + " via " + entry.creationSource, NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Owner: " + entry.currentOwner + " @ " + entry.currentLocation,
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Status: " + entry.status, NamedTextColor.YELLOW));
        var history = plugin.ledger().eventPageByUid(uid, page, PAGE_SIZE);
        int pages = Math.max(1, (history.total() + PAGE_SIZE - 1) / PAGE_SIZE);
        sender.sendMessage(Component.text("History page " + page + "/" + pages + " (" + history.total() + " events)",
                NamedTextColor.YELLOW));
        for (ItemMovementEvent event : history.events()) {
            sender.sendMessage(Component.text(TIME.format(new Date(event.timestamp())) + " " + event.action()
                    + " x" + event.count() + " " + value(event.from()) + " -> " + value(event.to()),
                    NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text("Duplicate alerts: " + plugin.ledger().duplicateAlertCount(uid),
                NamedTextColor.YELLOW));
        return true;
    }

    private boolean adminItem(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) return false;
        boolean give = args[0].equalsIgnoreCase("give");
        boolean remove = args[0].equalsIgnoreCase("remove");
        if (!give && !remove) return false;
        String permission = give ? PermissionNodes.ADMINITEM_GIVE : PermissionNodes.ADMINITEM_REMOVE;
        if (!plugin.permissions().canUse(sender, permission)) return denied(sender);
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) return fail(sender, "That player is not online.");
        Material material = Material.matchMaterial(args[2]);
        if (material == null || !material.isItem()) return fail(sender, "Unknown item: " + args[2]);
        int count = 1;
        try {
            if (args.length == 4) count = Math.max(1, Math.min(99, Integer.parseInt(args[3])));
        } catch (NumberFormatException exception) {
            return fail(sender, "Count must be a number from 1 to 99.");
        }
        String itemId = material.getKey().toString();
        String actor = sender.getName();
        if (give) {
            plugin.itemTracker().noteAdminAction(target, itemId, ItemAction.ADMIN_GIVE, actor);
            int remaining = target.getInventory().addItem(new ItemStack(material, count)).values().stream()
                    .mapToInt(ItemStack::getAmount).sum();
            int delivered = count - remaining;
            plugin.itemTracker().scanNow();
            plugin.actionLogger().log(actor, "ADMIN_ITEM_GIVE", target.getName(), delivered + "x " + itemId);
            if (delivered == 0) return fail(sender, target.getName() + "'s inventory is full.");
            return ok(sender, "Gave " + delivered + "x " + itemId + " to " + target.getName()
                    + (remaining > 0 ? " (" + remaining + " could not be delivered)" : "") + ".");
        }

        int remaining = count;
        for (ItemStack stack : target.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() != material || remaining == 0) continue;
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            remaining -= take;
        }
        int removed = count - remaining;
        if (removed == 0) return fail(sender, "No matching items were found.");
        plugin.itemTracker().noteAdminAction(target, itemId, ItemAction.ADMIN_REMOVE, actor);
        plugin.itemTracker().scanNow();
        plugin.actionLogger().log(actor, "ADMIN_ITEM_REMOVE", target.getName(), removed + "x " + itemId);
        return ok(sender, "Removed " + removed + "x " + itemId + " from " + target.getName()
                + (remaining > 0 ? " (" + remaining + " requested items were not found)" : "") + ".");
    }

    private static String value(String value) { return value == null ? "-" : value; }

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
