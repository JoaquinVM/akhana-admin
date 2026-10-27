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
    /** Purchases (orders), their shipping and payments, including creating products and changing their cost and price. */
    PURCHASES,
    /** Debts with people and their repayments, including voids (DEU-01 to DEU-04). */
    DEBTS,
    /** Internal consumptions valued at cost, including voids (CON-01). */
    CONSUMPTIONS,
    /** Finances summary. */
    FINANCES_SUMMARY,
    /** Manual incomes, expenses and transfers, including voids. */
    FINANCES_TRANSACTIONS,
    /** Financial accounts. */
    FINANCES_ACCOUNTS,
    /** Financial categories. */
    FINANCES_CATEGORIES,
    /** Reports of sales, profit, transactions and consumptions (FIN-10, CON-03). */
    REPORTS,
    /** Users and their roles. */
    USERS,
    /** One-time historical migration of the spreadsheets (FIN-11). */
    MIGRATION
}
