package com.echubbuck.admintools.common;

import java.util.*;

public class AdminRole {
    private String name;
    private Set<String> permissions;

    public AdminRole(String name, Set<String> permissions) {
        this.name = name;
        this.permissions = new HashSet<>(permissions);
    }

    public String name() { return name; }
    public Set<String> permissions() { return Collections.unmodifiableSet(permissions); }

    public boolean hasPermission(String node) {
        return permissions.contains("*") || permissions.contains(node);
    }
}
