package com.echubbuck.admintools.mixin;

import com.echubbuck.admintools.AdminToolsMod;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;DDD)V",
            at = @At("TAIL"))
    private void admintools$onSpawn(Level level, double x, double y, double z,
                                    ItemStack stack, double vx, double vy, double vz,
                                    CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) return;
        if (AdminToolsMod.getItemEventSink() != null) {
            AdminToolsMod.getItemEventSink().onItemEntitySpawn(stack);
        }
    }
}