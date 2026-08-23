package com.echubbuck.admintools.test;

import com.echubbuck.admintools.AdminToolsMod;
import com.echubbuck.admintools.common.ItemAction;
import com.echubbuck.admintools.common.ItemLedgerEntry;
import com.echubbuck.admintools.common.ItemMovementEvent;
import com.echubbuck.admintools.gui.InvSeeScreenHandler;
import com.echubbuck.admintools.container.ContainerAuditEvent;
import com.echubbuck.admintools.container.ContainerKey;
import com.echubbuck.admintools.identity.ItemUidComponent;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

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
    public void partialSplitGetsChildIdentity(GameTestHelper helper) {
        Player player = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack parent = new ItemStack(Items.DIAMOND, 8);
        AdminToolsMod.getItemEventSink().onGive(player, parent);
        UUID parentUid = AdminToolsMod.getItemIdentityManager().getIdentity(parent);

        ItemStack child = parent.split(3);
        UUID childUid = AdminToolsMod.getItemIdentityManager().getIdentity(child);
        ItemLedgerEntry childEntry = childUid == null ? null
                : AdminToolsMod.getItemLedger().entry(childUid.toString());

        if (parentUid != null && childUid != null && !parentUid.equals(childUid)
                && childEntry != null && "SPLIT".equals(childEntry.creationSource)
                && childEntry.parents.contains(parentUid.toString())
                && parent.getCount() == 5 && child.getCount() == 3) {
            helper.succeed();
        } else {
            helper.fail("partial split did not receive distinct lineage");
        }
    }

    @GameTest
    public void legitimateSplitAcrossPlayersIsNotDuplicate(GameTestHelper helper) {
        Player sender = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Player receiver = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack parent = new ItemStack(Items.EMERALD, 12);
        AdminToolsMod.getItemEventSink().onGive(sender, parent);
        ItemStack child = parent.split(4);
        UUID parentUid = AdminToolsMod.getItemIdentityManager().getIdentity(parent);
        UUID childUid = AdminToolsMod.getItemIdentityManager().getIdentity(child);
        sender.getInventory().setItem(0, parent);
        receiver.getInventory().setItem(0, child);

        AdminToolsMod.getItemDuplicateDetector().scanPlayers(java.util.List.of(sender, receiver));
        if (parentUid != null && childUid != null && !parentUid.equals(childUid)
                && duplicateCount(parentUid.toString()) == 0
                && duplicateCount(childUid.toString()) == 0) {
            helper.succeed();
        } else {
            helper.fail("legitimate partial transfer produced a duplicate alert");
        }
    }

    @GameTest
    public void resolvedDuplicateCanAlertAgain(GameTestHelper helper) {
        Player first = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Player second = helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack a = new ItemStack(Items.GOLD_INGOT, 1);
        ItemStack b = new ItemStack(Items.GOLD_INGOT, 1);
        UUID shared = UUID.randomUUID();
        ItemUidComponent.set(a, shared);
        ItemUidComponent.set(b, shared);
        first.getInventory().setItem(0, a);
        second.getInventory().setItem(0, b);

        AdminToolsMod.getItemDuplicateDetector().scanPlayers(java.util.List.of(first, second));
        int firstCount = duplicateCount(shared.toString());
        second.getInventory().setItem(0, ItemStack.EMPTY);
        AdminToolsMod.getItemDuplicateDetector().scanPlayers(java.util.List.of(first, second));
        second.getInventory().setItem(0, b);
        AdminToolsMod.getItemDuplicateDetector().scanPlayers(java.util.List.of(first, second));

        if (firstCount == 1 && duplicateCount(shared.toString()) == 2) {
            helper.succeed();
        } else {
            helper.fail("resolved UID did not generate a fresh duplicate alert");
        }
    }

    @GameTest
    public void missingLedgerEntryIsRecovered(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 7);
        UUID uid = UUID.randomUUID();
        ItemUidComponent.set(stack, uid);
        AdminToolsMod.getItemIdentityManager().ensureIdentity(stack, "UNKNOWN", null, "RecoveredPlayer");
        ItemLedgerEntry entry = AdminToolsMod.getItemLedger().entry(uid.toString());
        if (entry != null && "RECOVERED".equals(entry.creationSource) && entry.count == 7) {
            helper.succeed();
        } else {
            helper.fail("tagged stack with missing ledger entry was not recovered");
        }
    }

    @GameTest
    public void editableInvseeAttributesAdminMove(GameTestHelper helper) {
        ServerPlayer admin = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ServerPlayer target = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack stack = new ItemStack(Items.DIAMOND, 6);
        target.getInventory().setItem(0, stack);
        UUID uid = AdminToolsMod.getItemIdentityManager().ensureIdentity(
                stack, "TEST", target.getUUID(), target.getScoreboardName());
        InvSeeScreenHandler menu = new InvSeeScreenHandler(1, admin.getInventory(), target, true);

        menu.clicked(0, 0, ContainerInput.PICKUP, admin);

        boolean attributed = false;
        for (ItemMovementEvent event : AdminToolsMod.getItemLedger().eventsByUid(uid.toString())) {
            if (event.action() == ItemAction.ADMIN_MOVE
                    && admin.getScoreboardName().equals(event.actor())
                    && target.getScoreboardName().equals(event.from())
                    && admin.getScoreboardName().equals(event.to())
                    && event.count() == 6) {
                attributed = true;
                break;
            }
        }
        if (target.getInventory().getItem(0).isEmpty() && attributed) {
            helper.succeed();
        } else {
            helper.fail("editable invsee did not attribute the item move to the admin");
        }
    }

    @GameTest
    public void readOnlyInvseeRejectsPickup(GameTestHelper helper) {
        ServerPlayer admin = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ServerPlayer target = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        target.getInventory().setItem(0, new ItemStack(Items.EMERALD, 3));
        InvSeeScreenHandler menu = new InvSeeScreenHandler(1, admin.getInventory(), target, false);

        menu.clicked(0, 0, ContainerInput.PICKUP, admin);

        if (target.getInventory().getItem(0).getCount() == 3 && menu.getCarried().isEmpty()) {
            helper.succeed();
        } else {
            helper.fail("read-only invsee allowed an item pickup");
        }
    }

    @GameTest
    public void concurrentContainerViewersShareOneSession(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.BARREL);
        BarrelBlockEntity barrel = helper.getBlockEntity(pos, BarrelBlockEntity.class);
        ServerPlayer first = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ServerPlayer second = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var tracker = AdminToolsMod.getContainerAuditTracker();
        tracker.setEnabled(true);
        ContainerKey key = tracker.keyFor(barrel);
        int before = tracker.recentFor(key, 20).size();

        tracker.onOpen(first, barrel);
        tracker.onOpen(second, barrel);
        barrel.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        tracker.onClose(first, barrel);
        int afterFirstClose = tracker.recentFor(key, 20).size();
        tracker.onClose(second, barrel);
        java.util.List<ContainerAuditEvent> events = tracker.recentFor(key, 20);

        ContainerAuditEvent event = events.isEmpty() ? null : events.getLast();
        if (afterFirstClose == before && events.size() == before + 1
                && event != null && event.player().startsWith("multiple:")
                && event.added().getOrDefault("minecraft:iron_ingot", 0) == 4) {
            helper.succeed();
        } else {
            helper.fail("overlapping viewers were not combined into one shared session");
        }
    }

    @GameTest
    public void doubleChestUsesCanonicalCombinedAudit(GameTestHelper helper) {
        BlockPos leftPos = new BlockPos(1, 1, 1);
        BlockPos rightPos = new BlockPos(2, 1, 1);
        BlockState leftState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState rightState = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH)
                .setValue(ChestBlock.TYPE, ChestType.RIGHT);
        helper.setBlock(leftPos, leftState);
        helper.setBlock(rightPos, rightState);
        helper.setBlock(leftPos, leftState);
        ChestBlockEntity left = helper.getBlockEntity(leftPos, ChestBlockEntity.class);
        ChestBlockEntity right = helper.getBlockEntity(rightPos, ChestBlockEntity.class);
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var tracker = AdminToolsMod.getContainerAuditTracker();
        tracker.setEnabled(true);
        ContainerKey leftKey = tracker.keyFor(left);
        ContainerKey rightKey = tracker.keyFor(right);

        tracker.onOpen(player, left);
        right.setItem(0, new ItemStack(Items.DIAMOND, 2));
        tracker.onClose(player, left);
        java.util.List<ContainerAuditEvent> events = tracker.recentFor(leftKey, 20);
        ContainerAuditEvent event = events.isEmpty() ? null : events.getLast();

        if (leftKey.equals(rightKey) && event != null
                && event.added().getOrDefault("minecraft:diamond", 0) == 2) {
            helper.succeed();
        } else {
            helper.fail("double chest halves did not share a combined audit session");
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

    private static int duplicateCount(String uid) {
        int count = 0;
        for (ItemMovementEvent event : AdminToolsMod.getItemLedger().duplicateAlerts()) {
            if (uid.equals(event.uid())) count++;
        }
        return count;
    }
}
