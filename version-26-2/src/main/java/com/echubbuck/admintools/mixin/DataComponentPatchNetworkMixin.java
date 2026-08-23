package com.echubbuck.admintools.mixin;

import com.echubbuck.admintools.identity.ItemUidComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps server-authoritative item UIDs out of vanilla item-stack packets. */
@Mixin(DataComponentPatch.class)
public abstract class DataComponentPatchNetworkMixin {

    @Inject(method = "createStreamCodec", at = @At("RETURN"), cancellable = true)
    private static void admintools$stripUidFromNetworkPatches(
            CallbackInfoReturnable<StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch>> cir) {
        StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> original = cir.getReturnValue();
        cir.setReturnValue(new StreamCodec<>() {
            @Override
            public DataComponentPatch decode(RegistryFriendlyByteBuf buffer) {
                return original.decode(buffer);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, DataComponentPatch patch) {
                original.encode(buffer, ItemUidComponent.sanitizeForNetwork(patch));
            }
        });
    }
}
