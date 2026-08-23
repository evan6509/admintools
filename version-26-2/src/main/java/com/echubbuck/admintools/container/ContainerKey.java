package com.echubbuck.admintools.container;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Stable identity for a world-backed container block. */
public record ContainerKey(String dimension, String type, int x, int y, int z) {

    public static ContainerKey from(BlockEntity blockEntity) {
        if (blockEntity.getLevel() == null) return null;
        Identifier typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        String typeName = typeId == null ? blockEntity.getClass().getSimpleName() : typeId.toString();
        BlockPos position = blockEntity.getBlockPos();
        if (blockEntity instanceof ChestBlockEntity
                && blockEntity.getBlockState().getBlock() instanceof ChestBlock
                && blockEntity.getBlockState().getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos connected = ChestBlock.getConnectedBlockPos(position, blockEntity.getBlockState());
            if (compare(connected, position) < 0) position = connected;
        }
        return new ContainerKey(
                blockEntity.getLevel().dimension().identifier().toString(),
                typeName,
                position.getX(),
                position.getY(),
                position.getZ());
    }

    public BlockPos blockPos() {
        return new BlockPos(x, y, z);
    }

    public String id() {
        return dimension + "|" + type + "|" + x + "," + y + "," + z;
    }

    /** Human-readable location used in item-ledger movement events. */
    public String location() {
        return "container:" + dimension + ":" + x + "," + y + "," + z;
    }

    private static int compare(BlockPos first, BlockPos second) {
        int x = Integer.compare(first.getX(), second.getX());
        if (x != 0) return x;
        int y = Integer.compare(first.getY(), second.getY());
        return y != 0 ? y : Integer.compare(first.getZ(), second.getZ());
    }
}
