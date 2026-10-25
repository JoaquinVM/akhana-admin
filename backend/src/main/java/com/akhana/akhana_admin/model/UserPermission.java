package com.akhana.akhana_admin.model;

import java.util.Set;

public enum UserPermission {
    SALES_POS,
    SALES_HISTORY,
    SALES_CASH_HISTORY,
    FINANCES_SUMMARY,
    FINANCES_TRANSACTIONS,
    FINANCES_ACCOUNTS,
    FINANCES_CATEGORIES,
    CATALOG_PRODUCTS,
    CATALOG_CATEGORIES,
    CATALOG_TAGS,
    PURCHASES_SUPPLIERS,
    SECURITY_USERS;

    public static Set<String> allPermissionKeys() {
        return Set.of(
            SALES_POS.name(),
            SALES_HISTORY.name(),
            SALES_CASH_HISTORY.name(),
            FINANCES_SUMMARY.name(),
            FINANCES_TRANSACTIONS.name(),
            FINANCES_ACCOUNTS.name(),
            FINANCES_CATEGORIES.name(),
            CATALOG_PRODUCTS.name(),
            CATALOG_CATEGORIES.name(),
            CATALOG_TAGS.name(),
            PURCHASES_SUPPLIERS.name(),
            SECURITY_USERS.name()
        );
    }
}
