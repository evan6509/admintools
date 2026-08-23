package com.echubbuck.admintools.test;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemLedgerEntry;
import com.echubbuck.admintools.common.ItemMovementEvent;
import com.echubbuck.admintools.identity.ItemUidComponent;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.Map;
import java.util.UUID;

/**
 * Fabric gametest integration tests for the item identity / anti-duplication system.
 *
 * <p>Test-only; inert in normal play. Run on a gametest-enabled server
 * (e.g. {@code ./gradlew :version-26-2:runGametest}).
 */
public class AdminToolsGameTests {

    @GameTest
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

    @GameTest
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

    @GameTest
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

    @GameTest
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

    @GameTest
    public void uidMigratesFromLegacyComponent(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND, 1);
        UUID expected = UUID.randomUUID();
        stack.set(ItemUidComponent.LEGACY_TYPE, expected);

        UUID actual = ItemUidComponent.get(stack);
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        String stored = customData == null
                ? null
                : customData.copyTag().getString(ItemUidComponent.UID_KEY).orElse(null);

        if (expected.equals(actual)
                && expected.toString().equals(stored)
                && stack.get(ItemUidComponent.LEGACY_TYPE) == null) {
            helper.succeed();
        } else {
            helper.fail("legacy uid was not migrated into vanilla custom_data");
        }
    }

    @GameTest
    public void uidIsStrippedFromEncodedItemStack(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND, 1);
        CompoundTag customTag = new CompoundTag();
        customTag.putString("example:kept", "value");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
        ItemUidComponent.set(stack, UUID.randomUUID());

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), helper.getLevel().registryAccess());
        ItemStack.STREAM_CODEC.encode(buffer, stack);
        ItemStack decoded = ItemStack.STREAM_CODEC.decode(buffer);
        buffer.release();

        CustomData sentData = decoded.get(DataComponents.CUSTOM_DATA);
        CompoundTag sentTag = sentData == null ? new CompoundTag() : sentData.copyTag();

        if (!sentTag.contains(ItemUidComponent.UID_KEY)
                && ItemUidComponent.get(decoded) == null
                && "value".equals(sentTag.getString("example:kept").orElse(null))) {
            helper.succeed();
        } else {
            helper.fail("network sanitizer removed unrelated data or leaked the uid");
        }
    }

    @GameTest
    public void uidDoesNotAffectVanillaStackMatching(GameTestHelper helper) {
        ItemStack first = new ItemStack(Items.DIAMOND, 1);
        ItemStack second = new ItemStack(Items.DIAMOND, 1);
        ItemUidComponent.set(first, UUID.randomUUID());
        ItemUidComponent.set(second, UUID.randomUUID());

        if (ItemStack.isSameItemSameComponents(first, second)) {
            helper.succeed();
        } else {
            helper.fail("uid prevented otherwise identical stacks from matching");
        }
    }

    @GameTest
    public void legacyUidDoesNotRequireFabricClient(GameTestHelper helper) {
        Map<Identifier, Object2IntMap<Identifier>> registryMap =
                RegistrySyncManager.createAndPopulateRegistryMap();
        Identifier componentRegistryId = BuiltInRegistries.DATA_COMPONENT_TYPE.key().identifier();

        if (registryMap == null || !registryMap.containsKey(componentRegistryId)) {
            helper.succeed();
        } else {
            helper.fail("legacy uid component still makes Fabric registry sync require a client mod");
        }
    }
}
