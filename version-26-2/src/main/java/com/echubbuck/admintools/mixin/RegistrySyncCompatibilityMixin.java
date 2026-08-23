package com.echubbuck.admintools.mixin;

import com.echubbuck.admintools.identity.ItemUidComponent;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prevents the read-only legacy UID component from making Fabric API reject
 * vanilla clients. If another mod adds a custom data-component type, normal
 * Fabric registry compatibility checks remain in force.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager", remap = false)
public abstract class RegistrySyncCompatibilityMixin {

    @Inject(method = "createAndPopulateRegistryMap", at = @At("RETURN"), cancellable = true)
    private static void admintools$ignoreLegacyUidRegistryEntry(
            CallbackInfoReturnable<Map<Identifier, Object2IntMap<Identifier>>> cir) {
        Map<Identifier, Object2IntMap<Identifier>> registryMap = cir.getReturnValue();
        if (registryMap == null) return;

        Identifier componentRegistryId = BuiltInRegistries.DATA_COMPONENT_TYPE.key().identifier();
        Object2IntMap<Identifier> components = registryMap.get(componentRegistryId);
        if (components == null || !components.containsKey(ItemUidComponent.LEGACY_ID)) return;

        boolean hasAnotherModdedComponent = components.keySet().stream()
                .anyMatch(id -> !Identifier.DEFAULT_NAMESPACE.equals(id.getNamespace())
                        && !ItemUidComponent.LEGACY_ID.equals(id));
        if (hasAnotherModdedComponent) return;

        Map<Identifier, Object2IntMap<Identifier>> filtered = new LinkedHashMap<>(registryMap);
        filtered.remove(componentRegistryId);
        cir.setReturnValue(filtered.isEmpty() ? null : filtered);
    }
}
