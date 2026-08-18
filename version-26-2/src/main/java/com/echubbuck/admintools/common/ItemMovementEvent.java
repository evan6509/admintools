package com.echubbuck.admintools.common;

public record ItemMovementEvent(
        long timestamp,
        String uid,
        ItemAction action,
        String itemId,
        int count,
        String actor,
        String from,
        String to,
        String location) {

    public static ItemMovementEvent of(String uid, ItemAction action, String itemId, int count,
                                       String actor, String from, String to, String location) {
        return new ItemMovementEvent(System.currentTimeMillis(), uid, action, itemId, count, actor, from, to, location);
    }
}