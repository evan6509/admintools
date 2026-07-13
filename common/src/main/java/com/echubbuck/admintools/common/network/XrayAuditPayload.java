package com.echubbuck.admintools.common.network;

import java.util.UUID;

public record XrayAuditPayload(UUID targetUuid, String targetName, double riskScore, String riskLabel, double miningSpeed, double torchRatio, double oreExposureRate, int chunkUpdates) {}
