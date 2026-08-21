package com.echubbuck.admintools.mixin;

import com.echubbuck.admintools.AdminToolsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks the player-facing open/close lifecycle of chest-like block entities. */
@Mixin({ChestBlockEntity.class, BarrelBlockEntity.class, ShulkerBoxBlockEntity.class})
public abstract class ContainerOpenMixin {

    @Inject(method = "startOpen", at = @At("TAIL"))
    private void admintools$onOpen(ContainerUser user, CallbackInfo ci) {
        if (!(user instanceof ServerPlayer player)) return;
        if (AdminToolsMod.getContainerAuditTracker() == null) return;
        AdminToolsMod.getContainerAuditTracker().onOpen(player, (BlockEntity) (Object) this);
    }

    @Inject(method = "stopOpen", at = @At("HEAD"))
    private void admintools$onClose(ContainerUser user, CallbackInfo ci) {
        if (!(user instanceof ServerPlayer player)) return;
        if (AdminToolsMod.getContainerAuditTracker() == null) return;
        AdminToolsMod.getContainerAuditTracker().onClose(player, (BlockEntity) (Object) this);
    }
}
