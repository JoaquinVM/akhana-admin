package com.akhana.akhana_admin.service.impl;

import com.akhana.akhana_admin.dto.TagRequest;
import com.akhana.akhana_admin.dto.TagResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Tag;
import com.akhana.akhana_admin.model.TagStatus;
import com.akhana.akhana_admin.repository.TagRepository;
import com.akhana.akhana_admin.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> getAllTags(String search) {
        return getAllTags(search, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> getAllTags(String search, String status) {
        List<Tag> tags;
        boolean hasSearch = (search != null && !search.trim().isEmpty());
        String term = hasSearch ? search.trim() : null;

        if (status != null && !status.trim().isEmpty()) {
            String normStatus = status.trim().toUpperCase();
            if ("ELIMINADO".equals(normStatus)) {
                if (hasSearch) {
                    tags = tagRepository.searchTagsByStatus(term, TagStatus.ELIMINADO);
                } else {
                    tags = tagRepository.findByStatusOrderByNameAsc(TagStatus.ELIMINADO);
                }
                return tags.stream().map(TagResponse::fromEntity).toList();
            } else if ("ACTIVO".equals(normStatus)) {
                if (hasSearch) {
                    tags = tagRepository.searchTagsByStatus(term, TagStatus.ACTIVO);
                } else {
                    tags = tagRepository.findByStatusOrderByNameAsc(TagStatus.ACTIVO);
                }
                return tags.stream().map(TagResponse::fromEntity).toList();
            }
        }

        // Por defecto: Etiquetas activas (no eliminadas)
        if (hasSearch) {
            tags = tagRepository.searchTags(term, TagStatus.ELIMINADO);
        } else {
            tags = tagRepository.findByStatusNotOrderByNameAsc(TagStatus.ELIMINADO);
        }

        return tags.stream().map(TagResponse::fromEntity).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TagResponse getTagById(UUID id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Etiqueta no encontrada con ID: " + id));
        return TagResponse.fromEntity(tag);
    }

    @Override
    public TagResponse createTag(TagRequest request, String currentUsername) {
        String name = request.name().trim();

        // Validación exclusiva en Backend de unicidad de nombre (excluye ELIMINADO)
        if (tagRepository.existsByNameIgnoreCaseAndStatusNot(name, TagStatus.ELIMINADO)) {
            throw new DuplicateResourceException("Ya existe una etiqueta activa con el nombre especificado.");
        }

        Tag tag = Tag.builder()
                .name(name)
                .color(request.color().trim())
                .status(TagStatus.ACTIVO)
                .createdBy(currentUsername != null ? currentUsername : "SYSTEM")
                .createdAt(Instant.now())
                .build();

        Tag saved = tagRepository.save(tag);
        return TagResponse.fromEntity(saved);
    }

    @Override
    public TagResponse updateTag(UUID id, TagRequest request, String currentUsername) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Etiqueta no encontrada con ID: " + id));

        if (tag.getStatus() == TagStatus.ELIMINADO) {
            throw new IllegalStateException("Las etiquetas con estado ELIMINADO no pueden ser editadas.");
        }

        String name = request.name().trim();

        // Validación de unicidad de nombre excluyendo el registro actual y eliminados
        if (tagRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(name, TagStatus.ELIMINADO, id)) {
            throw new DuplicateResourceException("Ya existe otra etiqueta activa con el nombre especificado.");
        }

        tag.setName(name);
        tag.setColor(request.color().trim());
        tag.setUpdatedBy(currentUsername != null ? currentUsername : "SYSTEM");
        tag.setUpdatedAt(Instant.now());

        Tag updated = tagRepository.save(tag);
        return TagResponse.fromEntity(updated);
    }

    @Override
    public void deleteTag(UUID id, String currentUsername) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Etiqueta no encontrada con ID: " + id));

        if (tag.getStatus() == TagStatus.ELIMINADO) {
            throw new IllegalStateException("La etiqueta ya se encuentra en estado ELIMINADO.");
        }

        // Eliminación lógica obligatoria: nunca física
        tag.setStatus(TagStatus.ELIMINADO);
        tag.setDeletedBy(currentUsername != null ? currentUsername : "SYSTEM");
        tag.setDeletedAt(Instant.now());

        tagRepository.save(tag);
    }
}
