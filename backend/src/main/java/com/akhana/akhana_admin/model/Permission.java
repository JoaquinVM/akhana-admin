package com.akhana.akhana_admin.model;

/**
 * Per-screen permissions (table 2.2 of specs/requirements.md): whoever can open a screen can do everything in it.
 * Each one is a Spring Security authority with the same name, checked with {@code @PreAuthorize}; endpoints that
 * a screen only reads from another one (e.g. Products reads suppliers) also accept that screen. Roles group them.
 */
public enum Permission {
    /** POS: open and close the cash session, register and void sales, register and void cash expenses, quick products. */
    POS,
    /** Sales history. */
    SALES_HISTORY,
    /** Cash sessions history and their detail. */
    CASH_HISTORY,
    /** Products, including cost and sale price. */
    PRODUCTS,
    /** Product categories. */
    CATEGORIES,
    /** Tags. */
    TAGS,
    /** Suppliers. */
    SUPPLIERS,
    /** Finances summary. */
    FINANCES_SUMMARY,
    /** Manual incomes, expenses and transfers, including voids. */
    FINANCES_TRANSACTIONS,
    /** Financial accounts. */
    FINANCES_ACCOUNTS,
    /** Financial categories. */
    FINANCES_CATEGORIES,
    /** Users and their roles. */
    USERS
}
