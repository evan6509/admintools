package com.echubbuck.admintools.common.heuristic;

public record HeuristicRule(String dimension, double maxMiningSpeed, double minTorchRatio, double maxOreExposureRate, int maxChunkUpdates) {

    public static HeuristicRule overworld() {
        return new HeuristicRule("minecraft:overworld", 6.0, 0.1, 0.4, 50);
    }

    public static HeuristicRule nether() {
        return new HeuristicRule("minecraft:the_nether", 8.0, 0.05, 0.6, 60);
    }

    public static HeuristicRule end() {
        return new HeuristicRule("minecraft:the_end", 4.0, 0.0, 0.2, 30);
    }
}
