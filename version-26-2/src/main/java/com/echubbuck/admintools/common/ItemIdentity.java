package com.echubbuck.admintools.common;

import java.util.UUID;

public record ItemIdentity(UUID uid, long creationTime, String creationSource, UUID creatorUuid) {

    public static ItemIdentity create(String source, UUID creator) {
        return new ItemIdentity(UUID.randomUUID(), System.currentTimeMillis(), source, creator);
    }

    public String uidString() {
        return uid.toString();
    }
}