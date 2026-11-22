package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.KardexResponse;
import com.akhana.akhana_admin.dto.StockAdjustmentRequest;
import com.akhana.akhana_admin.dto.StockItemResponse;
import com.akhana.akhana_admin.dto.StockLevelResponse;
import com.akhana.akhana_admin.model.Consumption;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.Purchase;
import com.akhana.akhana_admin.model.PurchaseItem;
import com.akhana.akhana_admin.model.Sale;
import com.akhana.akhana_admin.model.SaleItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * INV: the stock of each product, moved by the documents that already exist (sales, consumptions, purchases) and by
 * counts and adjustments. Only a product that controls its stock now moves it (CAT-05, INV-18).
 */
public interface InventoryService {

    /**
     * INV-10: rejects, unless confirmed, the base quantities (by product) that are more than the recorded stock.
     * Returns whether some product was short, so the document is marked when it was confirmed.
     */
    boolean checkStock(Map<Product, BigDecimal> baseQuantities, boolean confirmed);

    /** CON-04, INV-02: the exits of a sale or a sale at cost, at the cost of each line. */
    void recordSale(Sale sale, String username);

    /** RG-03: a voided sale gives back what it took, at the cost it left at. */
    void reverseSale(Sale sale, String username);

    /** INV-12: an unidentified line assigned to its product leaves now, at the cost given to it. */
    void recordAssignedLine(Sale sale, SaleItem item, Product product, BigDecimal unitCost, String username);

    void recordConsumption(Consumption consumption, String username);

    void reverseConsumption(Consumption consumption, String username);

    /**
     * INV-07, INV-14, INV-15: what each line of the purchase must have put in the stock (delivered and active: its base
     * quantity and its value with its part of the shipping) less what it put. wasDelivered says whether it was
     * delivered before this change; the lines delivered before the product started do not move afterwards, except the
     * added ones.
     */
    void syncPurchase(Purchase purchase, boolean wasDelivered, Collection<PurchaseItem> addedLines, String comment,
                      String username);

    /** INV-07: the shipping of each active line, by its value, to the cent (the largest line takes the remainder). */
    Map<PurchaseItem, BigDecimal> shippingShares(Purchase purchase);

    /** CAT-05: a new product controls its stock from now, with 0; a special one never. */
    void startNewProduct(Product product);

    /** CAT-05: it stops controlling its stock, or starts again waiting for its next count. */
    void stockControlChanged(Product product, boolean controlled);

    boolean hasMovements(UUID productId);

    // Screens (Existencias and Kardex)

    List<StockItemResponse> getStock(UUID categoryId);

    List<StockLevelResponse> getLevels();

    KardexResponse getKardex(UUID productId, LocalDate from, LocalDate to);

    KardexResponse adjust(UUID productId, StockAdjustmentRequest request, String username);
}
