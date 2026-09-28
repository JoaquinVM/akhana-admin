package com.akhana.akhana_admin.service;

import com.akhana.akhana_admin.dto.TagRequest;
import com.akhana.akhana_admin.dto.TagResponse;
import com.akhana.akhana_admin.exception.DuplicateResourceException;
import com.akhana.akhana_admin.exception.ResourceNotFoundException;
import com.akhana.akhana_admin.model.Tag;
import com.akhana.akhana_admin.model.TagStatus;
import com.akhana.akhana_admin.repository.TagRepository;
import com.akhana.akhana_admin.service.impl.TagServiceImpl;
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
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private TagServiceImpl tagService;

    private Tag activeTag;
    private UUID tagId;

    @BeforeEach
    void setUp() {
        tagId = UUID.randomUUID();
        activeTag = Tag.builder()
                .id(tagId)
                .name("Vegano")
                .color("#164312")
                .status(TagStatus.ACTIVO)
                .createdBy("admin")
                .createdAt(Instant.parse("2026-09-28T10:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("Debe crear una etiqueta exitosamente con estado ACTIVO y datos de auditoría")
    void createTag_Success() {
        TagRequest request = new TagRequest("Orgánico", "#3c6a00");

        when(tagRepository.existsByNameIgnoreCaseAndStatusNot("Orgánico", TagStatus.ELIMINADO))
                .thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> {
            Tag t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TagResponse response = tagService.createTag(request, "admin");

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Orgánico");
        assertThat(response.color()).isEqualTo("#3c6a00");
        assertThat(response.status()).isEqualTo(TagStatus.ACTIVO);
        assertThat(response.createdBy()).isEqualTo("admin");
        assertThat(response.createdAt()).isNotNull();

        verify(tagRepository, times(1)).save(any(Tag.class));
    }

    @Test
    @DisplayName("Debe rechazar la creación si ya existe una etiqueta ACTIVA con el mismo nombre (case-insensitive)")
    void createTag_DuplicateName_ThrowsException() {
        TagRequest request = new TagRequest("vegano", "#5b7f52");

        when(tagRepository.existsByNameIgnoreCaseAndStatusNot("vegano", TagStatus.ELIMINADO))
                .thenReturn(true);

        assertThatThrownBy(() -> tagService.createTag(request, "admin"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Ya existe una etiqueta activa");

        verify(tagRepository, never()).save(any(Tag.class));
    }

    @Test
    @DisplayName("Debe permitir crear una etiqueta con nombre idéntico a una etiqueta ELIMINADA")
    void createTag_NameMatchesDeleted_Success() {
        TagRequest request = new TagRequest("Sin Gluten", "#2e5b27");

        when(tagRepository.existsByNameIgnoreCaseAndStatusNot("Sin Gluten", TagStatus.ELIMINADO))
                .thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> {
            Tag t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TagResponse response = tagService.createTag(request, "admin");

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Sin Gluten");
        verify(tagRepository, times(1)).save(any(Tag.class));
    }

    @Test
    @DisplayName("Debe retornar lista de etiquetas filtrada por estado ACTIVO")
    void getAllTags_FilterActivo() {
        when(tagRepository.findByStatusOrderByNameAsc(TagStatus.ACTIVO))
                .thenReturn(List.of(activeTag));

        List<TagResponse> responses = tagService.getAllTags(null, "ACTIVO");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Vegano");
        assertThat(responses.get(0).status()).isEqualTo(TagStatus.ACTIVO);
    }

    @Test
    @DisplayName("Debe retornar lista de etiquetas filtrada por estado ELIMINADO")
    void getAllTags_FilterEliminado() {
        Tag deletedTag = Tag.builder()
                .id(UUID.randomUUID())
                .name("Antiguo")
                .color("#475569")
                .status(TagStatus.ELIMINADO)
                .createdBy("admin")
                .deletedBy("admin")
                .deletedAt(Instant.now())
                .build();

        when(tagRepository.findByStatusOrderByNameAsc(TagStatus.ELIMINADO))
                .thenReturn(List.of(deletedTag));

        List<TagResponse> responses = tagService.getAllTags(null, "ELIMINADO");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Antiguo");
        assertThat(responses.get(0).status()).isEqualTo(TagStatus.ELIMINADO);
    }

    @Test
    @DisplayName("Debe filtrar etiquetas por búsqueda parcial insensible a mayúsculas y estado ACTIVO")
    void getAllTags_SearchAndStatus() {
        when(tagRepository.searchTagsByStatus("veg", TagStatus.ACTIVO))
                .thenReturn(List.of(activeTag));

        List<TagResponse> responses = tagService.getAllTags("veg", "ACTIVO");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Vegano");
    }

    @Test
    @DisplayName("Debe obtener una etiqueta por ID")
    void getTagById_Success() {
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(activeTag));

        TagResponse response = tagService.getTagById(tagId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(tagId);
        assertThat(response.name()).isEqualTo("Vegano");
    }

    @Test
    @DisplayName("Debe lanzar ResourceNotFoundException si la etiqueta no existe")
    void getTagById_NotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(tagRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.getTagById(nonExistentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(nonExistentId.toString());
    }

    @Test
    @DisplayName("Debe actualizar una etiqueta correctamente con datos de auditoría")
    void updateTag_Success() {
        TagRequest updateRequest = new TagRequest("Vegano Certificado", "#3b82f6");

        when(tagRepository.findById(tagId)).thenReturn(Optional.of(activeTag));
        when(tagRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(
                "Vegano Certificado", TagStatus.ELIMINADO, tagId))
                .thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TagResponse response = tagService.updateTag(tagId, updateRequest, "editor");

        assertThat(response.name()).isEqualTo("Vegano Certificado");
        assertThat(response.color()).isEqualTo("#3b82f6");
        assertThat(response.updatedBy()).isEqualTo("editor");
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Debe rechazar la actualización si el nuevo nombre colisiona con otra etiqueta activa")
    void updateTag_DuplicateName_ThrowsException() {
        TagRequest updateRequest = new TagRequest("Orgánico", "#164312");

        when(tagRepository.findById(tagId)).thenReturn(Optional.of(activeTag));
        when(tagRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(
                "Orgánico", TagStatus.ELIMINADO, tagId))
                .thenReturn(true);

        assertThatThrownBy(() -> tagService.updateTag(tagId, updateRequest, "editor"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Ya existe otra etiqueta activa");

        verify(tagRepository, never()).save(any(Tag.class));
    }

    @Test
    @DisplayName("Debe permitir actualizar una etiqueta manteniendo su mismo nombre sin lanzar colisión")
    void updateTag_SameName_Success() {
        TagRequest updateRequest = new TagRequest("Vegano", "#fabd0d");

        when(tagRepository.findById(tagId)).thenReturn(Optional.of(activeTag));
        when(tagRepository.existsByNameIgnoreCaseAndStatusNotAndIdNot(
                "Vegano", TagStatus.ELIMINADO, tagId))
                .thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TagResponse response = tagService.updateTag(tagId, updateRequest, "editor");

        assertThat(response.name()).isEqualTo("Vegano");
        assertThat(response.color()).isEqualTo("#fabd0d");
        assertThat(response.updatedBy()).isEqualTo("editor");
    }

    @Test
    @DisplayName("Debe eliminar lógicamente una etiqueta marcándola como ELIMINADO y registrando deletedBy/At")
    void deleteTag_Success() {
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(activeTag));
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tagService.deleteTag(tagId, "admin");

        assertThat(activeTag.getStatus()).isEqualTo(TagStatus.ELIMINADO);
        assertThat(activeTag.getDeletedBy()).isEqualTo("admin");
        assertThat(activeTag.getDeletedAt()).isNotNull();

        verify(tagRepository, times(1)).save(activeTag);
    }

    @Test
    @DisplayName("Debe lanzar excepción si se intenta eliminar una etiqueta que ya está ELIMINADO")
    void deleteTag_AlreadyDeleted_ThrowsException() {
        activeTag.setStatus(TagStatus.ELIMINADO);
        when(tagRepository.findById(tagId)).thenReturn(Optional.of(activeTag));

        assertThatThrownBy(() -> tagService.deleteTag(tagId, "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ELIMINADO");

        verify(tagRepository, never()).save(any(Tag.class));
    }
}
