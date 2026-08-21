package com.echubbuck.admintools.mixin;

import com.echubbuck.admintools.AdminToolsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts chunks actually sent to each player so the x-ray heuristic's
 * chunk-update signal reflects real network traffic.
 */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderMixin {

    @Inject(method = "sendChunk", at = @At("TAIL"))
    private static void admintools$onChunkSent(ServerGamePacketListenerImpl listener, ServerLevel level,
                                               LevelChunk chunk, CallbackInfo ci) {
        if (AdminToolsMod.getHeuristicTracker() == null) return;
        ServerPlayer player = listener.player;
        if (player != null) {
            AdminToolsMod.getHeuristicTracker().recordChunkUpdate(player.getUUID());
        }
    }
}
