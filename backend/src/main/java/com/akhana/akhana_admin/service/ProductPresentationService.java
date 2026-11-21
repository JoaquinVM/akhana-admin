package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductPresentationRequest;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductPresentation;
import com.akhana.akhana_admin.model.SaleUnit;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** CAT-15: packages of a product (the presentations of CAT-11 are the packages that are sold). */
public interface ProductPresentationService {

    /**
     * Applies the product form after the product fields were set: when the sale unit changed (previousSaleUnit is
     * not null and differs) the active packages are deactivated; then the requested list applies (null keeps the
     * current one). Returns the notices to show: loss-making packages and own prices left unchanged by a new price.
     */
    List<String> applyPresentations(Product product, List<ProductPresentationRequest> requests,
                                    BigDecimal previousSellPrice, SaleUnit previousSaleUnit, String username);

    /** The active package of the product that is sold whole, to sell or consume it, or an error if it is not. */
    ProductPresentation findSoldPresentation(Product product, UUID presentationId);

    /** The active package of the product, sold or not, for a purchase line (COM-29), or an error if it is gone. */
    ProductPresentation findActivePresentation(Product product, UUID presentationId);
}
