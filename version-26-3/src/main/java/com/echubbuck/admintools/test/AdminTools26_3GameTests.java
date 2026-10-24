package com.echubbuck.admintools.test;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemMovementEvent;
import com.echubbuck.admintools.identity.ItemUidComponent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/** Runtime regression coverage for Fabric/Minecraft 26.3-specific hooks. */
public class AdminTools26_3GameTests {

    @GameTest
    public void playerDropHookRecordsMovement(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(
                net.minecraft.world.level.GameType.SURVIVAL);
        String playerName = player.getScoreboardName();
        ItemStack stack = new ItemStack(Items.DIAMOND, 2);
        UUID uid = AdminToolsMod.getItemIdentityManager().ensureIdentity(
                stack, "TEST", player.getUUID(), playerName);

        ItemEntity dropped = player.drop(stack, true, Prediction.SERVER_ONLY);
        List<ItemMovementEvent> events = AdminToolsMod.getItemLedger().eventsByUid(uid.toString());
        long dropEvents = events.stream().filter(event -> event.action() == ItemAction.DROP
                && playerName.equals(event.actor()) && event.count() == 2).count();
        UUID droppedUid = dropped == null ? null : ItemUidComponent.get(dropped.getItem());

        if (dropped != null && uid.equals(droppedUid) && dropEvents == 1) {
            helper.succeed();
        } else {
            helper.fail("26.3 player drop was not attributed exactly once: dropped=" + (dropped != null)
                    + ", uidPreserved=" + uid.equals(droppedUid) + ", dropEvents=" + dropEvents);
        }
    }
}
