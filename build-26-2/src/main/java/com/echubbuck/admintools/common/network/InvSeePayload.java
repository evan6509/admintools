package com.echubbuck.admintools.common.network;

import java.util.UUID;

public record InvSeePayload(UUID targetUuid, String targetName, int syncId, ItemData[] slots) {

    public static final int MAX_SLOTS = 54;

    public record ItemData(int slot, String itemId, int count, String nbt) {}
}
