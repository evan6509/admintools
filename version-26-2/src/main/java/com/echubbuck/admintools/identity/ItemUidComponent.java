package com.echubbuck.admintools.identity;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores AdminTools item identities in vanilla {@code minecraft:custom_data}.
 *
 * <p>The legacy custom component remains registered so worlds written by older
 * AdminTools builds can still be loaded. It is migrated lazily on first
 * observation and is always removed from outbound network component patches.
 */
public final class ItemUidComponent {
    public static final String NAME = "uid";
    public static final String UID_KEY = "admintools:uid";
    public static final Identifier LEGACY_ID = Identifier.fromNamespaceAndPath("admintools", NAME);

    /** Read-only compatibility registration for item stacks saved by older releases. */
    public static final DataComponentType<UUID> LEGACY_TYPE = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            LEGACY_ID,
            DataComponentType.<UUID>builder()
                    .persistent(Codec.STRING.xmap(UUID::fromString, UUID::toString))
                    .build());

    private ItemUidComponent() {}

    public static UUID get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            String value = customData.copyTag().getString(UID_KEY).orElse(null);
            UUID uid = parse(value);
            if (uid != null) {
                // A partially migrated stack may carry both representations.
                stack.remove(LEGACY_TYPE);
                return uid;
            }
        }

        UUID legacy = stack.get(LEGACY_TYPE);
        if (legacy != null) {
            set(stack, legacy);
            return legacy;
        }
        return null;
    }

    public static void set(ItemStack stack, UUID uid) {
        if (stack == null || stack.isEmpty() || uid == null) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putString(UID_KEY, uid.toString()));
        stack.remove(LEGACY_TYPE);
    }

    public static boolean has(ItemStack stack) {
        return get(stack) != null;
    }

    public static void remove(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(UID_KEY));
        stack.remove(LEGACY_TYPE);
    }

    /**
     * Compares two component maps while ignoring only the AdminTools UID.
     * Other {@code minecraft:custom_data} keys remain significant.
     */
    public static boolean sameComponentsIgnoringUid(DataComponentMap a, DataComponentMap b) {
        if (a == b) return true;

        int sizeA = comparableComponentCount(a);
        int sizeB = comparableComponentCount(b);
        if (sizeA != sizeB) return false;

        for (TypedDataComponent<?> component : a) {
            DataComponentType<?> type = component.type();
            if (type == LEGACY_TYPE || type == DataComponents.CUSTOM_DATA) continue;
            if (!Objects.equals(component.value(), getRaw(b, type))) return false;
        }

        return Objects.equals(customDataWithoutUid(a), customDataWithoutUid(b));
    }

    /**
     * Removes identity metadata from an item component patch before it is sent
     * over the vanilla protocol. The original patch and server stack are not
     * mutated.
     */
    public static DataComponentPatch sanitizeForNetwork(DataComponentPatch patch) {
        if (patch == null || patch.isEmpty()) return patch;

        boolean needsSanitizing = false;
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            if (entry.getKey() == LEGACY_TYPE) {
                needsSanitizing = true;
                break;
            }
            if (entry.getKey() == DataComponents.CUSTOM_DATA && entry.getValue().isPresent()) {
                CustomData data = (CustomData) entry.getValue().get();
                if (data.copyTag().contains(UID_KEY)) {
                    needsSanitizing = true;
                    break;
                }
            }
        }
        if (!needsSanitizing) return patch;

        DataComponentPatch.Builder sanitized = DataComponentPatch.builder();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            DataComponentType<?> type = entry.getKey();
            Optional<?> value = entry.getValue();

            if (type == LEGACY_TYPE) continue;
            if (type == DataComponents.CUSTOM_DATA && value.isPresent()) {
                CompoundTag tag = ((CustomData) value.get()).copyTag();
                tag.remove(UID_KEY);
                if (!tag.isEmpty()) {
                    sanitized.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                }
                continue;
            }
            copyPatchEntry(sanitized, type, value);
        }
        return sanitized.build();
    }

    private static int comparableComponentCount(DataComponentMap map) {
        int size = 0;
        for (DataComponentType<?> type : map.keySet()) {
            if (type != LEGACY_TYPE && type != DataComponents.CUSTOM_DATA) size++;
        }
        return size;
    }

    private static CompoundTag customDataWithoutUid(DataComponentMap map) {
        CustomData customData = map.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        tag.remove(UID_KEY);
        return tag.isEmpty() ? null : tag;
    }

    private static UUID parse(String value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object getRaw(DataComponentMap map, DataComponentType<?> type) {
        return map.get((DataComponentType) type);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void copyPatchEntry(DataComponentPatch.Builder builder,
                                       DataComponentType<?> type,
                                       Optional<?> value) {
        if (value.isPresent()) {
            builder.set(TypedDataComponent.createUnchecked((DataComponentType) type, value.get()));
        } else {
            builder.remove((DataComponentType) type);
        }
    }
}
