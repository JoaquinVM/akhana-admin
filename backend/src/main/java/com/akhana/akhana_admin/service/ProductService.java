package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.ProductRequest;
import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.model.ProductStatus;

import java.util.List;
import java.util.UUID;

public interface ProductService {

    List<ProductResponse> getAllProducts(String search);

    List<ProductResponse> getAllProducts(String search, String status);

    ProductResponse getProductById(UUID id);

    ProductResponse createProduct(ProductRequest request, String currentUsername);

    ProductResponse updateProduct(UUID id, ProductRequest request, String currentUsername);

    ProductResponse changeProductStatus(UUID id, ProductStatus status, String currentUsername);

    void deleteProduct(UUID id, String currentUsername);
}
