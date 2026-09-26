package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.SupplierRequest;
import com.akhana.akhana_admin.dto.SupplierResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Supplier;
import com.akhana.akhana_admin.model.SupplierStatus;
import com.akhana.akhana_admin.repository.SupplierRepository;
import com.akhana.akhana_admin.service.impl.SupplierServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @InjectMocks
    private SupplierServiceImpl supplierService;

    private Supplier activeSupplier;
    private UUID supplierId;

    @BeforeEach
    void setUp() {
        supplierId = UUID.randomUUID();
        activeSupplier = Supplier.builder()
                .id(supplierId)
                .name("Distribuidora del Norte S.A.")
                .code("PROV-001")
                .description("Insumos botánicos y aceites")
                .phone("+56 9 8765 4321")
                .status(SupplierStatus.ACTIVO)
                .createdBy("admin")
                .createdAt(Instant.parse("2026-09-24T10:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("TEST-SRV-01: Debe crear un proveedor exitosamente con estado ACTIVO y datos de auditoría")
    void createSupplier_Success() {
        SupplierRequest request = new SupplierRequest(
                "Agroinsumos del Sur SpA",
                "PROV-002",
                "Fertilizantes y sustratos",
                "+56 9 1234 5678",
                null
        );

        when(supplierRepository.existsByNameIgnoreCaseAndStatusNot("Agroinsumos del Sur SpA", SupplierStatus.ELIMINADO))
                .thenReturn(false);
        when(supplierRepository.existsByCodeIgnoreCaseAndStatusNot("PROV-002", SupplierStatus.ELIMINADO))
                .thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SupplierResponse response = supplierService.createSupplier(request, "admin");

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Agroinsumos del Sur SpA");
        assertThat(response.code()).isEqualTo("PROV-002");
        assertThat(response.status()).isEqualTo(SupplierStatus.ACTIVO);
        assertThat(response.createdBy()).isEqualTo("admin");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedBy()).isNull();
        assertThat(response.deletedBy()).isNull();

        verify(supplierRepository, times(1)).save(any(Supplier.class));
    }

    @Test
    @DisplayName("TEST-SRV-02: Debe rechazar la creación si ya existe un proveedor ACTIVO/INACTIVO con el mismo nombre")
    void createSupplier_DuplicateName_ThrowsException() {
        SupplierRequest request = new SupplierRequest(
                "Distribuidora del Norte S.A.",
                "PROV-999",
                "Descripción",
                "+56 9 0000 0000",
                null
        );

        when(supplierRepository.existsByNameIgnoreCaseAndStatusNot("Distribuidora del Norte S.A.", SupplierStatus.ELIMINADO))
                .thenReturn(true);

        assertThatThrownBy(() -> supplierService.createSupplier(request, "admin"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("nombre");

        verify(supplierRepository, never()).save(any(Supplier.class));
    }

    @Test
    @DisplayName("TEST-SRV-03: Debe rechazar la creación si ya existe un proveedor ACTIVO/INACTIVO con el mismo código")
    void createSupplier_DuplicateCode_ThrowsException() {
        SupplierRequest request = new SupplierRequest(
                "Otro Nombre S.A.",
                "PROV-001",
                "Descripción",
                "+56 9 0000 0000",
                null
        );

        when(supplierRepository.existsByNameIgnoreCaseAndStatusNot("Otro Nombre S.A.", SupplierStatus.ELIMINADO))
                .thenReturn(false);
        when(supplierRepository.existsByCodeIgnoreCaseAndStatusNot("PROV-001", SupplierStatus.ELIMINADO))
                .thenReturn(true);

        assertThatThrownBy(() -> supplierService.createSupplier(request, "admin"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("código");

        verify(supplierRepository, never()).save(any(Supplier.class));
    }

    @Test
    @DisplayName("TEST-SRV-04: Debe permitir crear con nombre o código si el registro existente está ELIMINADO")
    void createSupplier_ReusesCodeOfDeleted_Success() {
        SupplierRequest request = new SupplierRequest(
                "Nombre Reutilizado",
                "PROV-001",
                "Descripción",
                "+56 9 0000 0000",
                null
        );

        // El repositorio responde false porque la búsqueda condiciona status != ELIMINADO
        when(supplierRepository.existsByNameIgnoreCaseAndStatusNot("Nombre Reutilizado", SupplierStatus.ELIMINADO))
                .thenReturn(false);
        when(supplierRepository.existsByCodeIgnoreCaseAndStatusNot("PROV-001", SupplierStatus.ELIMINADO))
                .thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        SupplierResponse response = supplierService.createSupplier(request, "admin");

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("PROV-001");
        verify(supplierRepository, times(1)).save(any(Supplier.class));
    }

    @Test
    @DisplayName("TEST-SRV-05: Listado debe omitir eliminados y retornar ordenado")
    void getAllSuppliers_WithoutSearch_ReturnsActiveAndInactive() {
        Supplier s2 = Supplier.builder()
                .id(UUID.randomUUID())
                .name("Zeta Proveedores")
                .code("PROV-003")
                .status(SupplierStatus.INACTIVO)
                .createdBy("admin")
                .createdAt(Instant.now())
                .build();

        when(supplierRepository.findByStatusNotOrderByNameAsc(SupplierStatus.ELIMINADO))
                .thenReturn(List.of(activeSupplier, s2));

        List<SupplierResponse> result = supplierService.getAllSuppliers(null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Distribuidora del Norte S.A.");
        assertThat(result.get(1).name()).isEqualTo("Zeta Proveedores");
        verify(supplierRepository, times(1)).findByStatusNotOrderByNameAsc(SupplierStatus.ELIMINADO);
    }

    @Test
    @DisplayName("TEST-SRV-05-B: Listado con filtro de estado ELIMINADO debe retornar eliminados")
    void getAllSuppliers_WithDeletedStatus_ReturnsDeletedOnly() {
        Supplier sDeleted = Supplier.builder()
                .id(UUID.randomUUID())
                .name("Proveedor Borrado")
                .code("PROV-DEL")
                .status(SupplierStatus.ELIMINADO)
                .createdBy("admin")
                .createdAt(Instant.now())
                .deletedBy("admin")
                .deletedAt(Instant.now())
                .build();

        when(supplierRepository.findByStatusOrderByNameAsc(SupplierStatus.ELIMINADO))
                .thenReturn(List.of(sDeleted));

        List<SupplierResponse> result = supplierService.getAllSuppliers(null, "ELIMINADO");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(SupplierStatus.ELIMINADO);
        verify(supplierRepository, times(1)).findByStatusOrderByNameAsc(SupplierStatus.ELIMINADO);
    }

    @Test
    @DisplayName("TEST-SRV-06: Búsqueda debe invocar búsqueda parcial excluyendo eliminados")
    void getAllSuppliers_WithSearchTerm_CallsSearch() {
        when(supplierRepository.searchSuppliers("norte", SupplierStatus.ELIMINADO))
                .thenReturn(List.of(activeSupplier));

        List<SupplierResponse> result = supplierService.getAllSuppliers("norte");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("PROV-001");
        verify(supplierRepository, times(1)).searchSuppliers("norte", SupplierStatus.ELIMINADO);
    }

    @Test
    @DisplayName("TEST-SRV-07: Debe actualizar exitosamente y registrar datos de edición conservando creación")
    void updateSupplier_Success() {
        SupplierRequest request = new SupplierRequest(
                "Distribuidora del Norte Modificada",
                "PROV-001",
                "Nueva descripción",
                "+56 9 9999 8888",
                SupplierStatus.INACTIVO
        );

        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(supplierRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(
                "Distribuidora del Norte Modificada", SupplierStatus.ELIMINADO, supplierId)).thenReturn(false);
        when(supplierRepository.existsByCodeIgnoreCaseAndStatusNotAndIdNot(
                "PROV-001", SupplierStatus.ELIMINADO, supplierId)).thenReturn(false);
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierResponse response = supplierService.updateSupplier(supplierId, request, "editor_user");

        assertThat(response.name()).isEqualTo("Distribuidora del Norte Modificada");
        assertThat(response.status()).isEqualTo(SupplierStatus.INACTIVO);
        assertThat(response.createdBy()).isEqualTo("admin");
        assertThat(response.createdAt()).isEqualTo(activeSupplier.getCreatedAt());
        assertThat(response.updatedBy()).isEqualTo("editor_user");
        assertThat(response.updatedAt()).isNotNull();

        verify(supplierRepository, times(1)).save(activeSupplier);
    }

    @Test
    @DisplayName("TEST-SRV-08: Debe rechazar actualización si colisiona con otro proveedor no eliminado")
    void updateSupplier_DuplicateOtherSupplier_ThrowsException() {
        SupplierRequest request = new SupplierRequest(
                "Nombre Existente en Otro",
                "PROV-001",
                "Desc",
                null,
                null
        );

        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(supplierRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(
                "Nombre Existente en Otro", SupplierStatus.ELIMINADO, supplierId)).thenReturn(true);

        assertThatThrownBy(() -> supplierService.updateSupplier(supplierId, request, "admin"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("nombre");

        verify(supplierRepository, never()).save(any(Supplier.class));
    }

    @Test
    @DisplayName("TEST-SRV-09: Debe ejecutar eliminación lógica registrando usuario y fecha")
    void deleteSupplier_LogicalDelete_Success() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        supplierService.deleteSupplier(supplierId, "supervisor");

        assertThat(activeSupplier.getStatus()).isEqualTo(SupplierStatus.ELIMINADO);
        assertThat(activeSupplier.getDeletedBy()).isEqualTo("supervisor");
        assertThat(activeSupplier.getDeletedAt()).isNotNull();

        verify(supplierRepository, times(1)).save(activeSupplier);
        verify(supplierRepository, never()).delete(any(Supplier.class));
    }

    @Test
    @DisplayName("TEST-SRV-10: Debe fallar si se intenta eliminar un proveedor ya ELIMINADO")
    void deleteSupplier_AlreadyDeleted_ThrowsException() {
        activeSupplier.setStatus(SupplierStatus.ELIMINADO);
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));

        assertThatThrownBy(() -> supplierService.deleteSupplier(supplierId, "supervisor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ya se encuentra en estado ELIMINADO");

        verify(supplierRepository, never()).save(any(Supplier.class));
    }
}
