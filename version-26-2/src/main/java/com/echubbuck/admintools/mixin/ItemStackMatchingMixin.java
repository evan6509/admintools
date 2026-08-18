package com.echubbuck.admintools.mixin;

import com.echubbuck.admintools.identity.ItemUidComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMatchingMixin {

    @Inject(method = "isSameItemSameComponents", at = @At("HEAD"), cancellable = true)
    private static void admintools$ignoreUidForMatching(ItemStack a, ItemStack b,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (!ItemStack.isSameItem(a, b)) {
            cir.setReturnValue(false);
            return;
        }
        // If neither stack carries a uid, fall through to vanilla behaviour.
        if (!ItemUidComponent.has(a) && !ItemUidComponent.has(b)) {
            return;
        }
        cir.setReturnValue(ItemUidComponent.sameComponentsIgnoringUid(a.getComponents(), b.getComponents()));
    }
}