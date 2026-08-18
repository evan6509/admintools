package com.echubbuck.admintools.identity;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ItemUidComponent {
    public static final String NAME = "uid";
    public static final Identifier ID = Identifier.fromNamespaceAndPath("admintools", NAME);

    public static final DataComponentType<UUID> TYPE;

    static {
        TYPE = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                ID,
                DataComponentType.<UUID>builder()
                        .persistent(Codec.STRING.xmap(UUID::fromString, UUID::toString))
                        .build());
    }

    private ItemUidComponent() {}

    public static UUID get(ItemStack stack) {
        return stack.get(TYPE);
    }

    public static void set(ItemStack stack, UUID uid) {
        stack.set(TYPE, uid);
    }

    public static boolean has(ItemStack stack) {
        return stack.get(TYPE) != null;
    }

    public static void remove(ItemStack stack) {
        stack.remove(TYPE);
    }

    /**
     * Compares two component maps for equality, ignoring the uid component so
     * that vanilla stacking/merging logic is unaffected by item identity.
     */
    public static boolean sameComponentsIgnoringUid(DataComponentMap a, DataComponentMap b) {
        if (a == b) return true;
        Map<DataComponentType<?>, Object> ma = collect(a);
        Map<DataComponentType<?>, Object> mb = collect(b);
        return ma.equals(mb);
    }

    private static Map<DataComponentType<?>, Object> collect(DataComponentMap map) {
        Map<DataComponentType<?>, Object> out = new HashMap<>();
        for (TypedDataComponent<?> tc : map) {
            if (!tc.type().equals(TYPE)) {
                out.put(tc.type(), tc.value());
            }
        }
        return out;
    }
}