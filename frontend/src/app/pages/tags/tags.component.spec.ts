import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TagsComponent } from './tags.component';
import { TagService } from '../../core/tag/tag.service';
import { Tag } from '../../core/tag/models/tag.models';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

describe('TagsComponent', () => {
  let component: TagsComponent;
  let fixture: ComponentFixture<TagsComponent>;
  let mockTagService: any;

  const mockTags: Tag[] = [
    {
      id: 'tag-1',
      name: 'Vegano',
      color: '#164312',
      status: 'ACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-28T10:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    },
    {
      id: 'tag-2',
      name: 'Biodegradable',
      color: '#3c6a00',
      status: 'ACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-28T11:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    },
    {
      id: 'tag-3',
      name: 'Descontinuado Antiguo',
      color: '#475569',
      status: 'ELIMINADO',
      createdBy: 'admin',
      createdAt: '2026-09-20T10:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: 'admin',
      deletedAt: '2026-09-25T15:00:00Z'
    }
  ];

  beforeEach(async () => {
    mockTagService = {
      getTags: vi.fn().mockReturnValue(of(mockTags)),
      getTagById: vi.fn(),
      createTag: vi.fn(),
      updateTag: vi.fn(),
      deleteTag: vi.fn().mockReturnValue(of(null))
    };

    await TestBed.configureTestingModule({
      imports: [TagsComponent],
      providers: [
        { provide: TagService, useValue: mockTagService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(TagsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('debe cargar etiquetas y ordenarlas ascendentemente por nombre (A-Z)', () => {
    expect(mockTagService.getTags).toHaveBeenCalledTimes(1);

    const filtered = component.filteredTags();
    // Filtro por defecto ACTIVO: excluye tag-3 (ELIMINADO)
    expect(filtered.length).toBe(2);

    // Orden A-Z: "Biodegradable" antes de "Vegano"
    expect(filtered[0].name).toBe('Biodegradable');
    expect(filtered[1].name).toBe('Vegano');
  });

  it('debe filtrar en tiempo real por nombre de forma insensible a mayúsculas', () => {
    component.searchTerm.set('veg');
    fixture.detectChanges();

    const filtered = component.filteredTags();
    expect(filtered.length).toBe(1);
    expect(filtered[0].name).toBe('Vegano');
  });

  it('debe cambiar al filtro ELIMINADO y mostrar etiquetas eliminadas', () => {
    component.setStatusFilter('ELIMINADO');
    expect(component.selectedStatusFilter()).toBe('ELIMINADO');
    expect(mockTagService.getTags).toHaveBeenCalledWith(undefined, 'ELIMINADO');
  });

  it('debe abrir el modal de creación con el formulario limpio', () => {
    component.openCreateModal();
    fixture.detectChanges();

    expect(component.isFormModalOpen()).toBe(true);
    expect(component.isEditing()).toBe(false);
    expect(component.tagForm.get('name')?.value).toBe('');
    expect(component.tagForm.get('color')?.value).toBe('');
  });

  it('debe abrir el modal de edición con los datos de la etiqueta seleccionada', () => {
    const target = mockTags[0];
    component.openEditModal(target);
    fixture.detectChanges();

    expect(component.isFormModalOpen()).toBe(true);
    expect(component.isEditing()).toBe(true);
    expect(component.selectedTagId()).toBe(target.id);
    expect(component.tagForm.get('name')?.value).toBe(target.name);
    expect(component.tagForm.get('color')?.value).toBe(target.color);
    expect(component.hasFormChanges()).toBe(false);
  });

  it('no debe abrir el modal de edición para una etiqueta ELIMINADA', () => {
    const deletedTag = mockTags[2];
    component.openEditModal(deletedTag);

    expect(component.isFormModalOpen()).toBe(false);
  });

  it('debe detectar cambios en el formulario al editar (dirty check)', () => {
    component.openEditModal(mockTags[0]);
    expect(component.hasFormChanges()).toBe(false);

    component.tagForm.patchValue({ name: 'Vegano Estricto' });
    expect(component.hasFormChanges()).toBe(true);

    // Restaurar valor original
    component.tagForm.patchValue({ name: mockTags[0].name });
    expect(component.hasFormChanges()).toBe(false);
  });

  it('debe solicitar confirmación de descarte al intentar cerrar un modal sucio', () => {
    component.openCreateModal();
    component.tagForm.patchValue({ name: 'Orgánico' });

    component.attemptCloseForm();
    expect(component.isDiscardConfirmOpen()).toBe(true);
    expect(component.isFormModalOpen()).toBe(true);

    component.discardChanges();
    expect(component.isDiscardConfirmOpen()).toBe(false);
    expect(component.isFormModalOpen()).toBe(false);
  });

  it('debe cerrar directamente el modal si no hay cambios', () => {
    component.openCreateModal();
    component.attemptCloseForm();

    expect(component.isDiscardConfirmOpen()).toBe(false);
    expect(component.isFormModalOpen()).toBe(false);
  });

  it('debe guardar una nueva etiqueta válidamente', () => {
    const newTag: Tag = {
      id: 'tag-new',
      name: 'Sin Gluten',
      color: '#3c6a00',
      status: 'ACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-28T12:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    };

    mockTagService.createTag.mockReturnValue(of(newTag));

    component.openCreateModal();
    component.tagForm.setValue({
      name: 'Sin Gluten',
      color: '#3c6a00'
    });

    component.saveTag();

    expect(mockTagService.createTag).toHaveBeenCalledWith({
      name: 'Sin Gluten',
      color: '#3c6a00'
    });
    expect(component.isFormModalOpen()).toBe(false);
    expect(component.tags().some(t => t.id === 'tag-new')).toBe(true);
  });

  it('debe manejar error 409 de unicidad devuelto por el backend', () => {
    mockTagService.createTag.mockReturnValue(
      throwError(() => ({ error: { message: 'Ya existe una etiqueta activa con el nombre especificado.' } }))
    );

    component.openCreateModal();
    component.tagForm.setValue({
      name: 'Vegano',
      color: '#164312'
    });

    component.saveTag();

    expect(component.formError()).toBe('Ya existe una etiqueta activa con el nombre especificado.');
    expect(component.isFormModalOpen()).toBe(true);
  });

  it('debe eliminar lógicamente una etiqueta y removerla del listado activo', () => {
    const target = mockTags[0];
    component.openDeleteModal(target);

    expect(component.isConfirmModalOpen()).toBe(true);
    expect(component.tagToDelete()?.id).toBe(target.id);

    component.confirmDelete();

    expect(mockTagService.deleteTag).toHaveBeenCalledWith(target.id);
    expect(component.isConfirmModalOpen()).toBe(false);
    expect(component.tags().some(t => t.id === target.id)).toBe(false);
  });

  it('debe abrir el modal de auditoría con la información correcta', () => {
    const target = mockTags[0];
    component.openAuditModal(target);

    expect(component.isAuditModalOpen()).toBe(true);
    expect(component.auditModalSubtitle()).toBe(target.name);
    expect(component.selectedAuditData()?.['createdBy']).toBe('admin');
  });
});
