package com.echubbuck.admintools.identity;

import com.echubbuck.admintools.common.ItemIdentity;
import com.echubbuck.admintools.common.ItemLedger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class ItemIdentityManager {
    private final ItemLedger ledger;

    public ItemIdentityManager(ItemLedger ledger) {
        this.ledger = ledger;
    }

    public ItemLedger ledger() {
        return ledger;
    }

    public UUID ensureIdentity(ItemStack stack, String source, UUID creator, String ownerName) {
        UUID existing = ItemUidComponent.get(stack);
        if (existing != null) return existing;
        return assignIdentity(stack, source, creator, ownerName);
    }

    public UUID assignIdentity(ItemStack stack, String source, UUID creator, String ownerName) {
        if (stack.isEmpty()) return null;
        UUID uid = UUID.randomUUID();
        ItemUidComponent.set(stack, uid);
        ItemIdentity identity = ItemIdentity.create(source, creator);
        ledger.registerIdentity(identity, itemId(stack), stack.getCount(), ownerName, "player:" + ownerName);
        return uid;
    }

    public UUID getIdentity(ItemStack stack) {
        return ItemUidComponent.get(stack);
    }

    public boolean hasIdentity(ItemStack stack) {
        return ItemUidComponent.has(stack);
    }

    public void removeIdentity(ItemStack stack, String status) {
        UUID uid = ItemUidComponent.get(stack);
        if (uid != null) {
            ledger.setStatus(uid.toString(), status);
            ItemUidComponent.remove(stack);
        }
    }

    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:empty";
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "minecraft:unknown" : key.toString();
    }
}