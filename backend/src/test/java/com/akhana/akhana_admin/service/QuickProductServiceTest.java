package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.QuickProductGroupRequest;
import com.akhana.akhana_admin.dto.QuickProductGroupResponse;
import com.akhana.akhana_admin.dto.ReorderGroupsRequest;
import com.akhana.akhana_admin.dto.ReorderItemsRequest;
import com.akhana.akhana_admin.model.Product;
import com.akhana.akhana_admin.model.ProductStatus;
import com.akhana.akhana_admin.model.QuickProductGroup;
import com.akhana.akhana_admin.model.QuickProductGroupItem;
import com.akhana.akhana_admin.repository.ProductRepository;
import com.akhana.akhana_admin.repository.QuickProductGroupRepository;
import com.akhana.akhana_admin.service.impl.QuickProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuickProductServiceTest {

    @Mock
    private QuickProductGroupRepository groupRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private QuickProductServiceImpl quickProductService;

    private Product product1;
    private Product product2;
    private QuickProductGroup group1;

    @BeforeEach
    void setUp() {
        product1 = Product.builder()
            .id(UUID.randomUUID())
            .code("PRD-001")
            .name("Café Americano")
            .sellPrice(new BigDecimal("12.00"))
            .status(ProductStatus.ACTIVO)
            .build();

        product2 = Product.builder()
            .id(UUID.randomUUID())
            .code("PRD-002")
            .name("Mocaccino")
            .sellPrice(new BigDecimal("18.00"))
            .status(ProductStatus.ACTIVO)
            .build();

        group1 = QuickProductGroup.builder()
            .id(UUID.randomUUID())
            .name("Bebidas Calientes")
            .displayOrder(0)
            .items(new ArrayList<>())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    @DisplayName("Debe crear un nuevo grupo de productos rápidos")
    void shouldCreateGroupSuccessfully() {
        when(groupRepository.count()).thenReturn(0L);
        when(productRepository.findById(product1.getId())).thenReturn(Optional.of(product1));
        when(groupRepository.save(any(QuickProductGroup.class))).thenAnswer(i -> {
            QuickProductGroup g = i.getArgument(0);
            g.setId(UUID.randomUUID());
            return g;
        });

        QuickProductGroupRequest req = new QuickProductGroupRequest("Cafetería", List.of(product1.getId()));
        QuickProductGroupResponse res = quickProductService.createGroup(req);

        assertThat(res).isNotNull();
        assertThat(res.name()).isEqualTo("Cafetería");
        assertThat(res.products()).hasSize(1);
        assertThat(res.products().get(0).name()).isEqualTo("Café Americano");
    }

    @Test
    @DisplayName("Debe actualizar grupo agregando nuevos productos sin duplicar existentes ni recrear entidades")
    void shouldUpdateGroupAddingProducts() {
        QuickProductGroupItem item1 = QuickProductGroupItem.builder()
            .id(UUID.randomUUID())
            .group(group1)
            .product(product1)
            .displayOrder(0)
            .build();
        group1.setItems(new ArrayList<>(List.of(item1)));

        when(groupRepository.findById(group1.getId())).thenReturn(Optional.of(group1));
        when(productRepository.findById(product2.getId())).thenReturn(Optional.of(product2));
        when(groupRepository.save(any(QuickProductGroup.class))).thenReturn(group1);

        QuickProductGroupRequest req = new QuickProductGroupRequest("Cafetería Actualizada", List.of(product1.getId(), product2.getId()));
        QuickProductGroupResponse res = quickProductService.updateGroup(group1.getId(), req);

        assertThat(res).isNotNull();
        assertThat(group1.getItems()).hasSize(2);
        assertThat(group1.getItems().get(0).getProduct().getId()).isEqualTo(product1.getId());
        assertThat(group1.getItems().get(0).getId()).isEqualTo(item1.getId()); // Mismo ID persistido, no recreado
        assertThat(group1.getItems().get(1).getProduct().getId()).isEqualTo(product2.getId());
    }

    @Test
    @DisplayName("Debe reordenar grupos de productos rápidos según lista de IDs")
    void shouldReorderGroups() {
        QuickProductGroup group2 = QuickProductGroup.builder()
            .id(UUID.randomUUID())
            .name("Repostería")
            .displayOrder(1)
            .items(new ArrayList<>())
            .build();

        when(groupRepository.findById(group2.getId())).thenReturn(Optional.of(group2));
        when(groupRepository.findById(group1.getId())).thenReturn(Optional.of(group1));
        when(groupRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(group2, group1));

        ReorderGroupsRequest req = new ReorderGroupsRequest(List.of(group2.getId(), group1.getId()));
        List<QuickProductGroupResponse> result = quickProductService.reorderGroups(req);

        assertThat(result).hasSize(2);
        assertThat(group2.getDisplayOrder()).isEqualTo(0);
        assertThat(group1.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("Debe reordenar items dentro de un grupo según lista de IDs de producto")
    void shouldReorderItemsInGroup() {
        QuickProductGroupItem item1 = QuickProductGroupItem.builder()
            .id(UUID.randomUUID())
            .group(group1)
            .product(product1)
            .displayOrder(0)
            .build();

        QuickProductGroupItem item2 = QuickProductGroupItem.builder()
            .id(UUID.randomUUID())
            .group(group1)
            .product(product2)
            .displayOrder(1)
            .build();

        group1.setItems(new ArrayList<>(List.of(item1, item2)));

        when(groupRepository.findById(group1.getId())).thenReturn(Optional.of(group1));
        when(groupRepository.save(any(QuickProductGroup.class))).thenReturn(group1);

        ReorderItemsRequest req = new ReorderItemsRequest(List.of(product2.getId(), product1.getId()));
        QuickProductGroupResponse res = quickProductService.reorderGroupItems(group1.getId(), req);

        assertThat(res).isNotNull();
        assertThat(item2.getDisplayOrder()).isEqualTo(0);
        assertThat(item1.getDisplayOrder()).isEqualTo(1);
    }
}
