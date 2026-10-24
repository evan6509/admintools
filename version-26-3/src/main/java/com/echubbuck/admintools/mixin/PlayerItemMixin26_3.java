package com.echubbuck.admintools.mixin;

import com.echubbuck.admintools.AdminToolsMod;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minecraft 26.3 moved the player drop implementation to LivingEntity and
 * added a Prediction argument. Filter the shared method back to players so
 * non-player entity drops do not enter the player item ledger.
 */
@Mixin(LivingEntity.class)
public abstract class PlayerItemMixin26_3 {

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"))
    private void admintools$onDrop(ItemStack stack, boolean includeThrow, Prediction prediction,
                                   CallbackInfoReturnable<ItemEntity> cir) {
        if (stack == null || stack.isEmpty()) return;
        Object self = (Object) this;
        if (!(self instanceof Player player)) return;
        if (AdminToolsMod.getItemEventSink() != null) {
            AdminToolsMod.getItemEventSink().onDrop(player, stack);
        }
    }
}
