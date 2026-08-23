package com.echubbuck.admintools.mixin;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.identity.ItemUidComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** Gives a partial split its own identity at the operation that creates it. */
@Mixin(ItemStack.class)
public abstract class ItemStackSplitMixin {

    @Inject(method = "split", at = @At("RETURN"))
    private void admintools$identifySplit(int amount, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack parent = (ItemStack) (Object) this;
        ItemStack child = cir.getReturnValue();
        // A full-stack move empties the source and must preserve its identity.
        if (parent.isEmpty() || child == null || child.isEmpty()) return;
        UUID parentUid = ItemUidComponent.get(parent);
        if (parentUid == null || AdminToolsMod.getItemIdentityManager() == null) return;
        AdminToolsMod.getItemIdentityManager().identifySplit(parentUid, child);
    }
}
