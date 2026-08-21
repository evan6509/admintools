package com.echubbuck.admintools.container;

import java.util.Map;

/** One player session in a world-backed container's audit log. */
public record ContainerAuditEvent(
        long openedAt,
        long closedAt,
        String player,
        String playerUuid,
        String dimension,
        String containerType,
        int x,
        int y,
        int z,
        Map<String, Integer> added,
        Map<String, Integer> removed) {

    public ContainerKey key() {
        return new ContainerKey(dimension, containerType, x, y, z);
    }
}
