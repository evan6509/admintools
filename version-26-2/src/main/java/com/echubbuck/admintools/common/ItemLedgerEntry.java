package com.echubbuck.admintools.common;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ItemLedgerEntry {
    public String uid;
    public String itemId;
    public int count;
    public long creationTime;
    public String creationSource;
    public String creatorUuid;
    public String creatorName;
    public String currentOwner;
    public String currentLocation;
    public List<String> parents = new ArrayList<>();
    public String status = "ACTIVE";
    public long lastSeen;

    public ItemLedgerEntry() {}

    public ItemLedgerEntry(ItemIdentity identity, String itemId, int count, String owner, String location) {
        this.uid = identity.uidString();
        this.itemId = itemId;
        this.count = count;
        this.creationTime = identity.creationTime();
        this.creationSource = identity.creationSource();
        this.creatorUuid = identity.creatorUuid() == null ? null : identity.creatorUuid().toString();
        this.currentOwner = owner;
        this.currentLocation = location;
        this.lastSeen = System.currentTimeMillis();
    }

    public UUID uidUUID() {
        return uid == null ? null : UUID.fromString(uid);
    }
}