package com.akhana.akhana_admin.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User roles. Each role groups the per-screen permissions of table 2.2 of specs/requirements.md:
 * the administrator opens every screen; the operator, the day-to-day screens of the store.
 */
public enum Role {
    OPERATOR(EnumSet.of(
        Permission.POS,
        Permission.SALES_HISTORY,
        Permission.CASH_HISTORY,
        Permission.PRODUCTS,
        Permission.CATEGORIES,
        Permission.TAGS,
        Permission.SUPPLIERS,
        Permission.PURCHASES,
        Permission.DEBTS
    )),
    ADMINISTRATOR(EnumSet.allOf(Permission.class));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public Set<String> permissionKeys() {
        return permissions.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    public static Set<String> allPermissionKeys() {
        return Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
