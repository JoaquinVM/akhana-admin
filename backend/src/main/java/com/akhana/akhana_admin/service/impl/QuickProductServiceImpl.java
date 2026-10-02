package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.ProductResponse;
import com.akhana.akhana_admin.dto.QuickProductGroupRequest;
import com.akhana.akhana_admin.dto.QuickProductGroupResponse;
import com.akhana.akhana_admin.dto.ReorderGroupsRequest;
import com.akhana.akhana_admin.dto.ReorderItemsRequest;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.model.QuickProductGroup;
import com.akhana.akhana_admin.model.QuickProductGroupItem;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.QuickProductGroupRepository;
import com.akhana.akhana_admin.service.QuickProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class QuickProductServiceImpl implements QuickProductService {

    private final QuickProductGroupRepository groupRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<QuickProductGroupResponse> getAllGroups() {
        return groupRepository.findAllByOrderByDisplayOrderAsc().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public QuickProductGroupResponse getGroupById(UUID id) {
        QuickProductGroup group = groupRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Grupo de productos rápidos no encontrado con ID: " + id));
        return mapToResponse(group);
    }

    @Override
    public QuickProductGroupResponse createGroup(QuickProductGroupRequest request) {
        int nextOrder = (int) groupRepository.count();

        QuickProductGroup group = QuickProductGroup.builder()
            .name(request.name().trim())
            .displayOrder(nextOrder)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        if (request.productIds() != null && !request.productIds().isEmpty()) {
            List<QuickProductGroupItem> items = new ArrayList<>();
            int itemOrder = 0;
            for (UUID prodId : request.productIds()) {
                Product product = productRepository.findById(prodId)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + prodId));
                if (product.getStatus() != ProductStatus.ELIMINADO) {
                    items.add(QuickProductGroupItem.builder()
                        .group(group)
                        .product(product)
                        .displayOrder(itemOrder++)
                        .createdAt(Instant.now())
                        .build());
                }
            }
            group.setItems(items);
        }

        QuickProductGroup saved = groupRepository.save(group);
        return mapToResponse(saved);
    }

    @Override
    public QuickProductGroupResponse updateGroup(UUID id, QuickProductGroupRequest request) {
        QuickProductGroup group = groupRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado con ID: " + id));

        group.setName(request.name().trim());
        group.setUpdatedAt(Instant.now());

        if (request.productIds() != null) {
            group.getItems().clear();
            int itemOrder = 0;
            for (UUID prodId : request.productIds()) {
                Product product = productRepository.findById(prodId)
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + prodId));
                if (product.getStatus() != ProductStatus.ELIMINADO) {
                    group.getItems().add(QuickProductGroupItem.builder()
                        .group(group)
                        .product(product)
                        .displayOrder(itemOrder++)
                        .createdAt(Instant.now())
                        .build());
                }
            }
        }

        QuickProductGroup saved = groupRepository.save(group);
        return mapToResponse(saved);
    }

    @Override
    public void deleteGroup(UUID id) {
        QuickProductGroup group = groupRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado con ID: " + id));
        groupRepository.delete(group);
    }

    @Override
    public List<QuickProductGroupResponse> reorderGroups(ReorderGroupsRequest request) {
        List<UUID> orderedIds = request.groupIds();
        for (int i = 0; i < orderedIds.size(); i++) {
            UUID gId = orderedIds.get(i);
            QuickProductGroup group = groupRepository.findById(gId)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado con ID: " + gId));
            group.setDisplayOrder(i);
            group.setUpdatedAt(Instant.now());
            groupRepository.save(group);
        }
        return getAllGroups();
    }

    @Override
    public QuickProductGroupResponse reorderGroupItems(UUID groupId, ReorderItemsRequest request) {
        QuickProductGroup group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Grupo no encontrado con ID: " + groupId));

        List<UUID> orderedProductIds = request.productIds();
        for (QuickProductGroupItem item : group.getItems()) {
            int newOrder = orderedProductIds.indexOf(item.getProduct().getId());
            if (newOrder != -1) {
                item.setDisplayOrder(newOrder);
            }
        }
        group.setUpdatedAt(Instant.now());
        QuickProductGroup saved = groupRepository.save(group);
        return mapToResponse(saved);
    }

    private QuickProductGroupResponse mapToResponse(QuickProductGroup group) {
        List<ProductResponse> products = group.getItems() != null
            ? group.getItems().stream()
                .sorted(Comparator.comparing(QuickProductGroupItem::getDisplayOrder))
                .map(item -> ProductResponse.fromEntity(item.getProduct()))
                .collect(Collectors.toList())
            : Collections.emptyList();

        return new QuickProductGroupResponse(
            group.getId(),
            group.getName(),
            group.getDisplayOrder(),
            group.getCreatedAt(),
            group.getUpdatedAt(),
            products
        );
    }
}
