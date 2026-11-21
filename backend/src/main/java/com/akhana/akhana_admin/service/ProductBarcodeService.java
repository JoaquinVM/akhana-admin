package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.BarcodePendingResponse;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductPresentation;

import java.util.List;
import java.util.UUID;

public interface ProductBarcodeService {

    // The product of an active barcode, also when it is inactive, so the POS can say so
    ProductResponse findByBarcode(String barcode);

    List<BarcodePendingResponse> findPending();

    ProductResponse addBarcode(UUID productId, String barcode, String username);

    ProductResponse markNoBarcode(UUID productId, String username);

    ProductResponse clearNoBarcode(UUID productId, String username);

    // The whole list of the product form; null keeps the current codes. Runs after the sale unit is set
    void applyBarcodes(Product product, List<String> barcodes, String username);

    // Deactivates every barcode of the product and of its packages: the product is deleted
    void deactivateAll(Product product, String username);

    // CAT-15: the barcode of a package of the product; blank removes it. Unique among products and packages
    void applyPresentationBarcode(Product product, ProductPresentation presentation, String barcode, String username);
}
