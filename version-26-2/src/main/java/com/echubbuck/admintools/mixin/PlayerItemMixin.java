package com.echubbuck.admintools.mixin;

import com.echubbuck.admintools.AdminToolsMod;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerItemMixin {

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"))
    private void admintools$onDrop(ItemStack stack, boolean includeThrow,
                                   CallbackInfoReturnable<ItemEntity> cir) {
        if (stack == null || stack.isEmpty()) return;
        if (AdminToolsMod.getItemEventSink() != null) {
            AdminToolsMod.getItemEventSink().onDrop((Player) (Object) this, stack);
        }
    }

}
