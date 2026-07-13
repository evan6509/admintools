package com.echubbuck.admintools.common.heuristic;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class HeuristicData {
    private final UUID playerUuid;
    private final Deque<Long> blockBreakTimes = new ConcurrentLinkedDeque<>();
    private int blocksBroken;
    private int torchesPlaced;
    private int oresExposed;
    private int chunkUpdates;
    private long lastActivity = System.currentTimeMillis();

    public HeuristicData(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID playerUuid() { return playerUuid; }

    public void recordBlockBreak() {
        blocksBroken++;
        blockBreakTimes.addLast(System.currentTimeMillis());
        if (blockBreakTimes.size() > 100) blockBreakTimes.removeFirst();
        lastActivity = System.currentTimeMillis();
    }

    public void recordTorchPlacement() {
        torchesPlaced++;
        lastActivity = System.currentTimeMillis();
    }

    public void recordOreExposure() {
        oresExposed++;
        lastActivity = System.currentTimeMillis();
    }

    public void recordChunkUpdate() {
        chunkUpdates++;
        lastActivity = System.currentTimeMillis();
    }

    public double miningSpeed() {
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000;
        return blockBreakTimes.stream().filter(t -> t > cutoff).count() / 60.0;
    }

    public double torchRatio() {
        return blocksBroken > 0 ? (double) torchesPlaced / blocksBroken : 0;
    }

    public double oreExposureRate() {
        return blocksBroken > 0 ? (double) oresExposed / blocksBroken : 0;
    }

    public int chunkUpdates() { return chunkUpdates; }
    public boolean isActive() { return System.currentTimeMillis() - lastActivity < 300_000; }
    public void reset() {
        blocksBroken = 0; torchesPlaced = 0; oresExposed = 0; chunkUpdates = 0;
        blockBreakTimes.clear();
    }
}
