package com.echubbuck.admintools.heuristic;

import com.echubbuck.admintools.common.heuristic.HeuristicData;
import com.echubbuck.admintools.common.heuristic.HeuristicEngine;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HeuristicTracker {
    private final HeuristicEngine engine;

    private static final Set<Block> ORES = Set.of(
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE,
            Blocks.ANCIENT_DEBRIS
    );

    public HeuristicTracker(HeuristicEngine engine) {
        this.engine = engine;
    }

    public HeuristicEngine getEngine() { return engine; }

    public void recordBreak(UUID playerUuid, Block block) {
        HeuristicData data = engine.getOrCreate(playerUuid);
        data.recordBlockBreak();
        if (ORES.contains(block)) {
            data.recordOreExposure();
        }
    }

    /** Called from BlockItemPlaceMixin for every successful block placement. */
    public void recordPlace(UUID playerUuid, Block block) {
        if (block == Blocks.TORCH || block == Blocks.SOUL_TORCH || block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN) {
            engine.getOrCreate(playerUuid).recordTorchPlacement();
        }
    }

    /** Called from PlayerChunkSenderMixin whenever a chunk is sent to a player. */
    public void recordChunkUpdate(UUID playerUuid) {
        engine.getOrCreate(playerUuid).recordChunkUpdate();
    }

    /**
     * Periodic maintenance: drops per-player data for players that have been
     * inactive past the activity window so the maps stay bounded.
     */
    public void pruneInactive() {
        for (Map.Entry<UUID, HeuristicData> e : engine.allData()) {
            if (!e.getValue().isActive()) {
                engine.forget(e.getKey());
            }
        }
    }
}
