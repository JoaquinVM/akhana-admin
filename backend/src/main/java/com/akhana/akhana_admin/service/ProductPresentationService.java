package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductPresentationRequest;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductPresentation;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** CAT-11: presentations of products sold by weight. */
public interface ProductPresentationService {

    /**
     * Applies the product form after the product fields were set: a product by unit has its presentations
     * deactivated; one by weight takes the requested list (null keeps the current one). Returns the notices to
     * show: loss-making presentations and fixed prices left unchanged by a new price per kilo.
     */
    List<String> applyPresentations(Product product, List<ProductPresentationRequest> requests,
                                    BigDecimal previousSellPrice, String username);

    /** The active presentation of the product to sell or consume, or an error if it is gone. */
    ProductPresentation findActivePresentation(Product product, UUID presentationId);
}
