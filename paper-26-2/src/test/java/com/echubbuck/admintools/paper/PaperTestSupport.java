package com.echubbuck.admintools.paper;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** In-memory API doubles: no Paper server behavior is claimed by these tests. */
final class PaperTestSupport {
    static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);
        PlayerInventory storage = inventory(PlayerInventory.class, 41);
        Inventory ender = inventory(Inventory.class, 27);
        when(player.getInventory()).thenReturn(storage);
        when(player.getEnderChest()).thenReturn(ender);
        return player;
    }

    static <T extends Inventory> T inventory(Class<T> type, int size) {
        T inventory = mock(type);
        ItemStack[] items = new ItemStack[size];
        when(inventory.getSize()).thenReturn(size);
        when(inventory.getMaxStackSize()).thenReturn(64);
        when(inventory.getItem(anyInt())).thenAnswer(call -> items[call.getArgument(0)]);
        doAnswer(call -> {
            items[call.getArgument(0)] = copy(call.getArgument(1));
            return null;
        }).when(inventory).setItem(anyInt(), nullable(ItemStack.class));
        when(inventory.getContents()).thenAnswer(call -> items.clone());
        if (inventory instanceof PlayerInventory storage) {
            when(storage.getStorageContents()).thenAnswer(call -> java.util.Arrays.copyOf(items, 36));
            doAnswer(call -> {
                ItemStack[] contents = call.getArgument(0);
                for (int i = 0; i < 36; i++) items[i] = copy(contents[i]);
                return null;
            }).when(storage).setStorageContents(any(ItemStack[].class));
        } else {
            when(inventory.getStorageContents()).thenAnswer(call -> items.clone());
        }
        return inventory;
    }

    static ItemStack copy(ItemStack item) { return item == null ? null : item.clone(); }
    static ItemStack item(Material type, int amount) { return new Stack(type, amount, new HashMap<>()); }

    private static final class Stack extends ItemStack {
        private final Material material;
        private int amount;
        private final Map<NamespacedKey, String> values;
        private final PersistentDataContainer pdc = mock(PersistentDataContainer.class);

        Stack(Material material, int amount, Map<NamespacedKey, String> values) {
            this.material = material;
            this.amount = amount;
            this.values = values;
            when(pdc.get(any(), any())).thenAnswer(call -> values.get(call.getArgument(0)));
            doAnswer(call -> { values.put(call.getArgument(0), call.getArgument(2)); return null; })
                    .when(pdc).set(any(), any(), any());
            doAnswer(call -> { values.remove(call.getArgument(0)); return null; }).when(pdc).remove(any());
        }
        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public int getMaxStackSize() { return material == Material.DIAMOND_SWORD ? 1 : 64; }
        @Override public boolean isEmpty() { return amount <= 0 || material == Material.AIR; }
        @Override public Stack clone() { return new Stack(material, amount, new HashMap<>(values)); }
        @Override public boolean isSimilar(ItemStack other) {
            return other instanceof Stack stack && material == stack.material && values.equals(stack.values);
        }
        @Override public boolean equals(Object other) {
            return other instanceof ItemStack stack && amount == stack.getAmount() && isSimilar(stack);
        }
        @Override public int hashCode() { return material.hashCode() * 31 + amount; }
        @Override public PersistentDataContainer getPersistentDataContainer() { return pdc; }
        @Override public boolean editPersistentDataContainer(Consumer<PersistentDataContainer> consumer) {
            consumer.accept(pdc); return true;
        }
        @Override public byte[] serializeAsBytes() {
            return (material.name() + ":" + amount + ":" + values).getBytes(StandardCharsets.UTF_8);
        }
    }
}
