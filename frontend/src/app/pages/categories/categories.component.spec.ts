import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CategoriesComponent } from './categories.component';
import { CategoryService } from '../../core/category/category.service';
import { Category } from '../../core/category/models/category.models';
import { of, throwError } from 'rxjs';
import { By } from '@angular/platform-browser';
import { vi } from 'vitest';

describe('CategoriesComponent', () => {
  let component: CategoriesComponent;
  let fixture: ComponentFixture<CategoriesComponent>;
  let mockCategoryService: any;

  const mockCategories: Category[] = [
    {
      id: 'cat-1',
      name: 'Sustratos y Tierras',
      description: 'Mezclas orgánicas y turbas',
      color: '#164312',
      status: 'ACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-26T10:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    },
    {
      id: 'cat-2',
      name: 'Aromaterapia',
      description: 'Aceites esenciales puros',
      color: '#3c6a00',
      status: 'ACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-26T11:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    },
    {
      id: 'cat-3',
      name: 'Herramientas Descontinuadas',
      description: 'Palas y tijeras',
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
    mockCategoryService = {
      getCategories: vi.fn().mockReturnValue(of(mockCategories)),
      getCategoryById: vi.fn(),
      createCategory: vi.fn(),
      updateCategory: vi.fn(),
      deleteCategory: vi.fn().mockReturnValue(of(null))
    };

    await TestBed.configureTestingModule({
      imports: [CategoriesComponent],
      providers: [
        { provide: CategoryService, useValue: mockCategoryService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CategoriesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('debe cargar categorías y ordenarlas ascendentemente por nombre (A-Z)', () => {
    expect(mockCategoryService.getCategories).toHaveBeenCalledTimes(1);

    const filtered = component.filteredCategories();
    // Filtro por defecto ACTIVO: excluye cat-3 (ELIMINADO)
    expect(filtered.length).toBe(2);

    // Orden A-Z: "Aromaterapia" antes de "Sustratos y Tierras"
    expect(filtered[0].name).toBe('Aromaterapia');
    expect(filtered[1].name).toBe('Sustratos y Tierras');
  });

  it('debe filtrar en tiempo real por nombre de forma insensible a mayúsculas', () => {
    component.searchTerm.set('aroma');
    fixture.detectChanges();

    const filtered = component.filteredCategories();
    expect(filtered.length).toBe(1);
    expect(filtered[0].name).toBe('Aromaterapia');
  });

  it('debe cambiar al filtro ELIMINADO y mostrar categorías eliminadas', () => {
    component.setStatusFilter('ELIMINADO');
    expect(component.selectedStatusFilter()).toBe('ELIMINADO');
    expect(mockCategoryService.getCategories).toHaveBeenCalledWith(undefined, 'ELIMINADO');
  });

  it('debe abrir el modal de creación con el formulario limpio', () => {
    component.openCreateModal();
    fixture.detectChanges();

    expect(component.isFormModalOpen()).toBe(true);
    expect(component.isEditing()).toBe(false);
    expect(component.categoryForm.get('name')?.value).toBe('');
    expect(component.categoryForm.get('color')?.value).toBe('');
  });

  it('debe abrir el modal de edición con los datos de la categoría seleccionada', () => {
    const target = mockCategories[0];
    component.openEditModal(target);
    fixture.detectChanges();

    expect(component.isFormModalOpen()).toBe(true);
    expect(component.isEditing()).toBe(true);
    expect(component.selectedCategoryId()).toBe(target.id);
    expect(component.categoryForm.get('name')?.value).toBe(target.name);
    expect(component.categoryForm.get('color')?.value).toBe(target.color);
    expect(component.hasFormChanges()).toBe(false);
  });

  it('no debe abrir el modal de edición para una categoría ELIMINADA', () => {
    const deletedCategory = mockCategories[2];
    component.openEditModal(deletedCategory);

    expect(component.isFormModalOpen()).toBe(false);
  });

  it('debe detectar cambios en el formulario al editar (dirty check)', () => {
    component.openEditModal(mockCategories[0]);
    expect(component.hasFormChanges()).toBe(false);

    component.categoryForm.patchValue({ name: 'Sustratos y Tierras Modificado' });
    expect(component.hasFormChanges()).toBe(true);

    // Restaurar valor original
    component.categoryForm.patchValue({ name: mockCategories[0].name });
    expect(component.hasFormChanges()).toBe(false);
  });

  it('debe solicitar confirmación de descarte al intentar cerrar un modal sucio', () => {
    component.openCreateModal();
    component.categoryForm.patchValue({ name: 'Nueva' });

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

  it('debe guardar una nueva categoría válidamente', () => {
    const newCat: Category = {
      id: 'cat-new',
      name: 'Fitoterapia',
      description: 'Hierbas',
      color: '#3c6a00',
      status: 'ACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-26T12:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    };

    mockCategoryService.createCategory.mockReturnValue(of(newCat));

    component.openCreateModal();
    component.categoryForm.setValue({
      name: 'Fitoterapia',
      description: 'Hierbas',
      color: '#3c6a00'
    });

    component.saveCategory();

    expect(mockCategoryService.createCategory).toHaveBeenCalledWith({
      name: 'Fitoterapia',
      description: 'Hierbas',
      color: '#3c6a00'
    });
    expect(component.isFormModalOpen()).toBe(false);
    expect(component.categories().some(c => c.id === 'cat-new')).toBe(true);
  });

  it('debe manejar error 409 de unicidad devuelto por el backend', () => {
    mockCategoryService.createCategory.mockReturnValue(
      throwError(() => ({ error: { message: 'Ya existe una categoría activa con el nombre especificado.' } }))
    );

    component.openCreateModal();
    component.categoryForm.setValue({
      name: 'Aromaterapia',
      description: null,
      color: '#164312'
    });

    component.saveCategory();

    expect(component.formError()).toBe('Ya existe una categoría activa con el nombre especificado.');
    expect(component.isFormModalOpen()).toBe(true);
  });

  it('debe eliminar lógicamente una categoría y removerla del listado activo', () => {
    const target = mockCategories[0];
    component.openDeleteModal(target);

    expect(component.isConfirmModalOpen()).toBe(true);
    expect(component.categoryToDelete()?.id).toBe(target.id);

    component.confirmDelete();

    expect(mockCategoryService.deleteCategory).toHaveBeenCalledWith(target.id);
    expect(component.isConfirmModalOpen()).toBe(false);
    expect(component.categories().some(c => c.id === target.id)).toBe(false);
  });

  it('debe abrir el modal de auditoría con la información correcta', () => {
    const target = mockCategories[0];
    component.openAuditModal(target);

    expect(component.isAuditModalOpen()).toBe(true);
    expect(component.auditModalSubtitle()).toBe(target.name);
    expect(component.selectedAuditData()?.['createdBy']).toBe('admin');
  });
});
