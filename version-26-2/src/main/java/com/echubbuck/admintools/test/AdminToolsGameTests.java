package com.echubbuck.admintools.test;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemLedgerEntry;
import com.echubbuck.admintools.common.ItemMovementEvent;
import com.echubbuck.admintools.identity.ItemUidComponent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * Fabric gametest integration tests for the item identity / anti-duplication system.
 *
 * <p>Test-only; inert in normal play. Run on a gametest-enabled server
 * (e.g. {@code ./gradlew :version-26-2:runGametest}).
 */
public class AdminToolsGameTests {

    @GameTest(structure = "")
    public void giveCreatesIdentity(GameTestHelper helper) {
        Player p = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack stack = new ItemStack(Items.DIAMOND, 1);
        AdminToolsMod.getItemEventSink().onGive(p, stack);

        UUID uid = AdminToolsMod.getItemIdentityManager().getIdentity(stack);
        if (uid == null) {
            helper.fail("give did not create an identity");
            return;
        }
        ItemLedgerEntry entry = AdminToolsMod.getItemLedger().entry(uid.toString());
        if (entry == null) {
            helper.fail("identity not in ledger");
            return;
        }
        if (!"ADMIN_GIVE".equals(entry.creationSource)) {
            helper.fail("expected ADMIN_GIVE source, got " + entry.creationSource);
            return;
        }
        helper.succeed();
    }

    @GameTest(structure = "")
    public void pickupPreservesIdentity(GameTestHelper helper) {
        Player p = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 5);
        AdminToolsMod.getItemEventSink().onGive(p, stack);
        UUID first = AdminToolsMod.getItemIdentityManager().getIdentity(stack);
        if (first == null) {
            helper.fail("give did not create an identity");
            return;
        }

        AdminToolsMod.getItemEventSink().onPickup(p, stack);
        UUID second = AdminToolsMod.getItemIdentityManager().getIdentity(stack);
        if (first.equals(second)) {
            helper.succeed();
        } else {
            helper.fail("identity changed across a move: " + first + " -> " + second);
        }
    }

    @GameTest(structure = "")
    public void assignCreatesDistinctIdentities(GameTestHelper helper) {
        Player p = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack a = new ItemStack(Items.OAK_LOG, 40);
        ItemStack b = new ItemStack(Items.OAK_LOG, 24);

        UUID uidA = AdminToolsMod.getItemIdentityManager().assignIdentity(a, "SPLIT", p.getUUID(), p.getScoreboardName());
        UUID uidB = AdminToolsMod.getItemIdentityManager().assignIdentity(b, "SPLIT", p.getUUID(), p.getScoreboardName());

        if (!uidA.equals(uidB) && AdminToolsMod.getItemLedger().entry(uidA.toString()) != null
                && AdminToolsMod.getItemLedger().entry(uidB.toString()) != null) {
            helper.succeed();
        } else {
            helper.fail("split identities not distinct/registered");
        }
    }

    @GameTest(structure = "")
    public void duplicateIsDetected(GameTestHelper helper) {
        Player p1 = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Player p2 = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        String name1 = p1.getScoreboardName();
        String name2 = p2.getScoreboardName();

        ItemStack s1 = new ItemStack(Items.DIAMOND, 1);
        ItemStack s2 = new ItemStack(Items.DIAMOND, 1);
        UUID shared = UUID.randomUUID();
        ItemUidComponent.set(s1, shared);
        ItemUidComponent.set(s2, shared);
        p1.getInventory().setItem(0, s1);
        p2.getInventory().setItem(0, s2);

        AdminToolsMod.getItemDuplicateDetector().scanPlayers(java.util.List.of(p1, p2));

        boolean alerted = false;
        for (ItemMovementEvent e : AdminToolsMod.getItemLedger().duplicateAlerts()) {
            if (e.action() == ItemAction.DUPLICATE_DETECTED && shared.toString().equals(e.uid())) {
                alerted = true;
                break;
            }
        }
        if (alerted) {
            helper.succeed();
        } else {
            helper.fail("duplicate of " + shared + " across " + name1 + " / " + name2 + " was not detected");
        }
    }
}