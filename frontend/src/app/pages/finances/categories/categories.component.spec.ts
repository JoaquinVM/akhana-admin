import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FinancialCategoriesComponent } from './categories.component';
import { FinancialCategoryService } from '../../../core/finance/financial-category.service';
import { of } from 'rxjs';
import { FinancialCategory } from '../../../core/finance/models/finance.models';

describe('FinancialCategoriesComponent', () => {
  let component: FinancialCategoriesComponent;
  let fixture: ComponentFixture<FinancialCategoriesComponent>;
  let mockCategoryService: any;

  const mockCategories: FinancialCategory[] = [
    {
      id: 'cat-sales',
      name: 'Ventas',
      type: 'INGRESO',
      color: '#2E5B27',
      isSalesDefault: true,
      createdBy: 'admin',
      createdAt: '2026-10-07T10:00:00'
    },
    {
      id: 'cat-capital',
      name: 'Aporte de Capital',
      type: 'INGRESO',
      color: '#3B82F6',
      isSalesDefault: false,
      createdBy: 'admin',
      createdAt: '2026-10-07T11:00:00'
    },
    {
      id: 'cat-supplies',
      name: 'Insumos y Limpieza',
      type: 'GASTO',
      color: '#EF4444',
      isSalesDefault: false,
      createdBy: 'admin',
      createdAt: '2026-10-07T12:00:00'
    }
  ];

  beforeEach(async () => {
    mockCategoryService = {
      getCategories: vi.fn().mockReturnValue(of(mockCategories)),
      createCategory: vi.fn().mockReturnValue(of(mockCategories[1])),
      updateCategory: vi.fn().mockReturnValue(of(mockCategories[1])),
      deleteCategory: vi.fn().mockReturnValue(of(void 0))
    };

    await TestBed.configureTestingModule({
      imports: [FinancialCategoriesComponent],
      providers: [
        { provide: FinancialCategoryService, useValue: mockCategoryService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(FinancialCategoriesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse y cargar las categorías iniciales', () => {
    expect(component).toBeTruthy();
    expect(component.categories().length).toBe(3);
    expect(component.filteredCategories().length).toBe(3);
  });

  it('debe filtrar categorías por tipo INGRESO y GASTO', () => {
    component.setTypeFilter('INGRESO');
    expect(component.filteredCategories().length).toBe(2);
    expect(component.filteredCategories().every(c => c.type === 'INGRESO')).toBe(true);

    component.setTypeFilter('GASTO');
    expect(component.filteredCategories().length).toBe(1);
    expect(component.filteredCategories()[0].name).toBe('Insumos y Limpieza');

    component.setTypeFilter('TODOS');
    expect(component.filteredCategories().length).toBe(3);
  });

  it('debe filtrar categorías por término de búsqueda en tiempo real', () => {
    component.searchTerm.set('capital');
    expect(component.filteredCategories().length).toBe(1);
    expect(component.filteredCategories()[0].name).toBe('Aporte de Capital');
  });

  it('debe abrir modal de creación y guardar nueva categoría con color', () => {
    component.openCreateModal();
    expect(component.isFormModalOpen()).toBe(true);
    expect(component.isEditing()).toBe(false);
    expect(component.categoryForm.get('color')?.value).toBe('#2E5B27');

    component.categoryForm.setValue({
      name: 'Servicios Básicos',
      type: 'GASTO',
      color: '#8B5CF6',
      isSalesDefault: false
    });

    component.saveCategory();

    expect(mockCategoryService.createCategory).toHaveBeenCalledWith({
      name: 'Servicios Básicos',
      type: 'GASTO',
      color: '#8B5CF6',
      isSalesDefault: false
    });
    expect(component.isFormModalOpen()).toBe(false);
  });

  it('debe abrir modal de edición y deshabilitar el campo tipo cargando su color', () => {
    const cat = mockCategories[1];
    component.openEditModal(cat);

    expect(component.isFormModalOpen()).toBe(true);
    expect(component.isEditing()).toBe(true);
    expect(component.categoryForm.get('type')?.disabled).toBe(true);
    expect(component.categoryForm.get('color')?.value).toBe('#3B82F6');

    component.categoryForm.patchValue({
      name: 'Aporte de Socios',
      color: '#10B981',
      isSalesDefault: false
    });

    component.saveCategory();

    expect(mockCategoryService.updateCategory).toHaveBeenCalledWith('cat-capital', {
      name: 'Aporte de Socios',
      type: 'INGRESO',
      color: '#10B981',
      isSalesDefault: false
    });
  });

  it('debe proteger la categoría de ventas predeterminada impidiendo su eliminación', () => {
    const salesCat = mockCategories[0];
    const normalCat = mockCategories[1];

    expect(component.canDelete(salesCat)).toBe(false);
    expect(component.canDelete(normalCat)).toBe(true);

    component.openDeleteModal(salesCat);
    expect(component.isDeleteModalOpen()).toBe(false);

    component.openDeleteModal(normalCat);
    expect(component.isDeleteModalOpen()).toBe(true);
  });

  it('debe confirmar la eliminación y recargar categorías', () => {
    component.openDeleteModal(mockCategories[1]);
    expect(component.isDeleteModalOpen()).toBe(true);

    component.confirmDelete();

    expect(mockCategoryService.deleteCategory).toHaveBeenCalledWith('cat-capital');
    expect(component.isDeleteModalOpen()).toBe(false);
    expect(mockCategoryService.getCategories).toHaveBeenCalledTimes(2);
  });

  it('debe abrir el modal de auditoría con los metadatos correspondientes', () => {
    component.openAuditModal(mockCategories[0]);
    expect(component.isAuditModalOpen()).toBe(true);
    expect(component.auditModalSubtitle()).toBe('Ventas');
    expect(component.selectedAuditData()?.['createdBy']).toBe('admin');
  });
});
