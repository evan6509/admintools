package com.echubbuck.admintools.identity;

import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemMovementEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ItemDuplicateDetector {
    private final ItemIdentityManager identityManager;
    private final Set<String> activeAlerts = new HashSet<>();
    private boolean detectCreative = false;

    public ItemDuplicateDetector(ItemIdentityManager identityManager) {
        this.identityManager = identityManager;
    }

    public void setDetectCreative(boolean detectCreative) {
        this.detectCreative = detectCreative;
    }

    public void scan(ServerLevel world) {
        scanPlayers(world.getServer().getPlayerList().getPlayers());
    }

    public void scanPlayers(java.util.List<? extends Player> players) {
        Map<String, List<String>> uidLocations = new HashMap<>();
        Map<String, String> uidItem = new HashMap<>();

        for (Player player : players) {
            if (!detectCreative && player.isCreative()) continue;
            String name = player.getScoreboardName();
            collect(player.getInventory(), uidLocations, uidItem, name);
            collect(player.getEnderChestInventory(), uidLocations, uidItem, name);
        }

        Set<String> seenThisScan = new HashSet<>();
        for (Map.Entry<String, List<String>> e : uidLocations.entrySet()) {
            String uid = e.getKey();
            List<String> locations = e.getValue();
            seenThisScan.add(uid);
            if (locations.size() >= 2) {
                if (activeAlerts.add(uid)) {
                    String locs = String.join(", ", locations);
                    identityManager.ledger().recordDuplicate(ItemMovementEvent.of(
                            uid, ItemAction.DUPLICATE_DETECTED, uidItem.getOrDefault(uid, ""),
                            locations.size(), null, null, null, locs));
                    notifyAdmins(players, "§c[AdminTools] Duplicate item detected: §f" + uid
                            + " §7(" + uidItem.getOrDefault(uid, "?") + ") in: " + locs);
                }
            }
        }
        activeAlerts.retainAll(seenThisScan);
    }

    private void collect(net.minecraft.world.Container container, Map<String, List<String>> uidLocations,
                         Map<String, String> uidItem, String playerName) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.isEmpty()) continue;
            UUID uid = identityManager.getIdentity(s);
            if (uid == null) continue;
            String u = uid.toString();
            uidLocations.computeIfAbsent(u, k -> new ArrayList<>()).add(playerName + " slot " + i);
            uidItem.putIfAbsent(u, identityManager.itemId(s));
        }
    }

    private void notifyAdmins(java.util.List<? extends Player> players, String message) {
        for (Player player : players) {
            if (player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }
}