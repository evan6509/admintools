package com.echubbuck.admintools.identity;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

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
     *
     * <p>Allocation-free: runs on the hot path (every stack merge/split check),
     * so it counts non-uid components and compares values via lookups instead
     * of building temporary maps.
     */
    public static boolean sameComponentsIgnoringUid(DataComponentMap a, DataComponentMap b) {
        if (a == b) return true;
        int sizeA = 0;
        for (DataComponentType<?> type : a.keySet()) {
            if (type != TYPE) sizeA++;
        }
        int sizeB = 0;
        for (DataComponentType<?> type : b.keySet()) {
            if (type != TYPE) sizeB++;
        }
        if (sizeA != sizeB) return false;
        for (TypedDataComponent<?> tc : a) {
            DataComponentType<?> type = tc.type();
            if (type == TYPE) continue;
            if (!java.util.Objects.equals(tc.value(), getRaw(b, type))) return false;
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object getRaw(DataComponentMap map, DataComponentType<?> type) {
        return map.get((DataComponentType) type);
    }
}