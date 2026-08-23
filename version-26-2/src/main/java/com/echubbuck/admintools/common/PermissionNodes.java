package com.echubbuck.admintools.common;

import java.util.List;

/** Permission nodes understood by AdminTools' per-player access manager. */
public final class PermissionNodes {
    public static final String ALL = "admintools.*";
    public static final String INVSEE = "admintools.invsee";
    public static final String INVSEE_EDIT = "admintools.invsee.edit";
    public static final String ENDERSEE = "admintools.endersee";
    public static final String ITEMTRACE = "admintools.itemtrace";
    public static final String CONTAINERTRACE = "admintools.containertrace";
    public static final String ADMINITEM_ALL = "admintools.adminitem.*";
    public static final String ADMINITEM_GIVE = "admintools.adminitem.give";
    public static final String ADMINITEM_REMOVE = "admintools.adminitem.remove";

    public static final List<String> VALUES = List.of(
            ALL,
            INVSEE,
            INVSEE_EDIT,
            ENDERSEE,
            ITEMTRACE,
            CONTAINERTRACE,
            ADMINITEM_ALL,
            ADMINITEM_GIVE,
            ADMINITEM_REMOVE);

    private PermissionNodes() {}
}
