package com.akhana.akhana_admin.dto;

import java.util.UUID;

/** INV-05: a count of a category; without one, of the products scanned or searched. */
public record StockCountRequest(UUID categoryId) {
}
