package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.SupplierRequest;
import com.akhana.akhana_admin.dto.SupplierResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Supplier;
import com.akhana.akhana_admin.model.SupplierStatus;
import com.akhana.akhana_admin.repository.SupplierRepository;
import com.akhana.akhana_admin.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SupplierServiceImpl implements SupplierService {

    private final SupplierRepository supplierRepository;

    @Override
    @Transactional(readOnly = true)
    public List<SupplierResponse> getAllSuppliers(String search) {
        return getAllSuppliers(search, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierResponse> getAllSuppliers(String search, String status) {
        List<Supplier> suppliers;
        boolean hasSearch = (search != null && !search.trim().isEmpty());
        String term = hasSearch ? search.trim() : null;

        if (status != null && !status.trim().isEmpty()) {
            String normStatus = status.trim().toUpperCase();
            if ("ACTIVO".equals(normStatus) || "INACTIVO".equals(normStatus) || "ELIMINADO".equals(normStatus)) {
                SupplierStatus targetStatus = SupplierStatus.valueOf(normStatus);
                if (hasSearch) {
                    suppliers = supplierRepository.searchSuppliersByStatus(term, targetStatus);
                } else {
                    suppliers = supplierRepository.findByStatusOrderByNameAsc(targetStatus);
                }
                return suppliers.stream().map(SupplierResponse::fromEntity).toList();
            }
        }

        // Por defecto: Activos e Inactivos (excluye ELIMINADO)
        if (hasSearch) {
            suppliers = supplierRepository.searchSuppliers(term, SupplierStatus.ELIMINADO);
        } else {
            suppliers = supplierRepository.findByStatusNotOrderByNameAsc(SupplierStatus.ELIMINADO);
        }
        return suppliers.stream()
                .map(SupplierResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(UUID id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado con ID: " + id));
        return SupplierResponse.fromEntity(supplier);
    }

    @Override
    public SupplierResponse createSupplier(SupplierRequest request, String currentUsername) {
        String name = request.name().trim();
        String code = request.code().trim();

        // Validación exclusiva en Backend de unicidad de nombre (excluye ELIMINADO)
        if (supplierRepository.existsByNameIgnoreCaseAndStatusNot(name, SupplierStatus.ELIMINADO)) {
            throw new DuplicateResourceException("Ya existe un proveedor activo o inactivo con el nombre especificado.");
        }

        // Validación exclusiva en Backend de unicidad de código (excluye ELIMINADO)
        if (supplierRepository.existsByCodeIgnoreCaseAndStatusNot(code, SupplierStatus.ELIMINADO)) {
            throw new DuplicateResourceException("Ya existe un proveedor activo o inactivo con el código especificado.");
        }

        Supplier supplier = Supplier.builder()
                .name(name)
                .code(code)
                .description(request.description() != null ? request.description().trim() : null)
                .phone(request.phone() != null ? request.phone().trim() : null)
                .status(SupplierStatus.ACTIVO)
                .createdBy(currentUsername != null ? currentUsername : "SYSTEM")
                .createdAt(Instant.now())
                .build();

        Supplier saved = supplierRepository.save(supplier);
        return SupplierResponse.fromEntity(saved);
    }

    @Override
    public SupplierResponse updateSupplier(UUID id, SupplierRequest request, String currentUsername) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado con ID: " + id));

        if (supplier.getStatus() == SupplierStatus.ELIMINADO) {
            throw new IllegalStateException("Los proveedores con estado ELIMINADO no pueden ser editados.");
        }

        String name = request.name().trim();
        String code = request.code().trim();

        // Validación de unicidad de nombre excluyendo el registro actual y eliminados
        if (supplierRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(name, SupplierStatus.ELIMINADO, id)) {
            throw new DuplicateResourceException("Ya existe otro proveedor activo o inactivo con el nombre especificado.");
        }

        // Validación de unicidad de código excluyendo el registro actual y eliminados
        if (supplierRepository.existsByCodeIgnoreCaseAndStatusNotAndIdNot(code, SupplierStatus.ELIMINADO, id)) {
            throw new DuplicateResourceException("Ya existe otro proveedor activo o inactivo con el código especificado.");
        }

        supplier.setName(name);
        supplier.setCode(code);
        supplier.setDescription(request.description() != null ? request.description().trim() : null);
        supplier.setPhone(request.phone() != null ? request.phone().trim() : null);

        if (request.status() != null && request.status() != SupplierStatus.ELIMINADO) {
            supplier.setStatus(request.status());
        }

        supplier.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        supplier.setUpdatedAt(Instant.now());

        Supplier updated = supplierRepository.save(supplier);
        return SupplierResponse.fromEntity(updated);
    }

    @Override
    public void deleteSupplier(UUID id, String currentUsername) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado con ID: " + id));

        if (supplier.getStatus() == SupplierStatus.ELIMINADO) {
            throw new IllegalStateException("El proveedor ya se encuentra en estado ELIMINADO.");
        }

        // Eliminación lógica obligatoria: nunca física
        supplier.setStatus(SupplierStatus.ELIMINADO);
        supplier.setDeletedBy(currentUsername != null ? currentUsername : "SYSTEM");
        supplier.setDeletedAt(Instant.now());

        supplierRepository.save(supplier);
    }
}
