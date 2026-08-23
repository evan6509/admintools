package com.echubbuck.admintools.common;

import java.util.UUID;

public record ItemIdentity(UUID uid, long creationTime, String creationSource, UUID creatorUuid) {

    public static ItemIdentity create(String source, UUID creator) {
        return create(UUID.randomUUID(), source, creator);
    }

    public static ItemIdentity create(UUID uid, String source, UUID creator) {
        return new ItemIdentity(uid, System.currentTimeMillis(), source, creator);
    }

    public String uidString() {
        return uid.toString();
    }
}
