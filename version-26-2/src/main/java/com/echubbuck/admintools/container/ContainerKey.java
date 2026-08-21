package com.echubbuck.admintools.container;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;

/** Stable identity for a world-backed container block. */
public record ContainerKey(String dimension, String type, int x, int y, int z) {

    public static ContainerKey from(BlockEntity blockEntity) {
        if (blockEntity.getLevel() == null) return null;
        Identifier typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        String typeName = typeId == null ? blockEntity.getClass().getSimpleName() : typeId.toString();
        return new ContainerKey(
                blockEntity.getLevel().dimension().identifier().toString(),
                typeName,
                blockEntity.getBlockPos().getX(),
                blockEntity.getBlockPos().getY(),
                blockEntity.getBlockPos().getZ());
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
}
