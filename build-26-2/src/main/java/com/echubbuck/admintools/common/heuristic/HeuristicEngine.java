package com.echubbuck.admintools.common.heuristic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HeuristicEngine {
    private final Map<String, HeuristicRule> rules = new ConcurrentHashMap<>();
    private final Map<UUID, HeuristicData> playerData = new ConcurrentHashMap<>();
    private final Map<UUID, Double> riskScores = new ConcurrentHashMap<>();

    public HeuristicEngine() {
        rules.put("minecraft:overworld", HeuristicRule.overworld());
        rules.put("minecraft:the_nether", HeuristicRule.nether());
        rules.put("minecraft:the_end", HeuristicRule.end());
    }

    public HeuristicData getOrCreate(UUID uuid) {
        return playerData.computeIfAbsent(uuid, HeuristicData::new);
    }

    public void addRule(String dimension, HeuristicRule rule) {
        rules.put(dimension, rule);
    }

    public double calculateRisk(UUID uuid, String dimension) {
        HeuristicData data = playerData.get(uuid);
        HeuristicRule rule = rules.getOrDefault(dimension, HeuristicRule.overworld());
        if (data == null) return 0.0;

        double score = 0.0;
        if (data.miningSpeed() > rule.maxMiningSpeed()) score += 25.0;
        if (data.torchRatio() < rule.minTorchRatio()) score += 25.0;
        if (data.oreExposureRate() > rule.maxOreExposureRate()) score += 25.0;
        if (data.chunkUpdates() > rule.maxChunkUpdates()) score += 25.0;

        riskScores.put(uuid, score);
        return score;
    }

    public double getRiskScore(UUID uuid) {
        return riskScores.getOrDefault(uuid, 0.0);
    }

    public String getRiskLabel(double score) {
        if (score >= 75) return "CRITICAL";
        if (score >= 50) return "SUSPICIOUS";
        if (score >= 25) return "WATCH";
        return "NORMAL";
    }

    public HeuristicData getData(UUID uuid) {
        return playerData.get(uuid);
    }

    public void reset(UUID uuid) {
        HeuristicData data = playerData.get(uuid);
        if (data != null) data.reset();
        riskScores.remove(uuid);
    }
}
