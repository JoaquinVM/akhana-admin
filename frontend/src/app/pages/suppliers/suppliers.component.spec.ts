import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SuppliersComponent } from './suppliers.component';
import { SupplierService } from '../../core/supplier/supplier.service';
import { of, throwError } from 'rxjs';
import { Supplier } from '../../core/supplier/models/supplier.models';
import { ModalComponent } from '../../shared/components/modal/modal.component';
import { By } from '@angular/platform-browser';

describe('SuppliersComponent', () => {
  let component: SuppliersComponent;
  let fixture: ComponentFixture<SuppliersComponent>;
  let mockSupplierService: any;

  const mockSuppliers: Supplier[] = [
    {
      id: 'sup-1',
      name: 'Distribuidora del Norte S.A.',
      code: 'PROV-001',
      description: 'Insumos botánicos',
      phone: '+56 9 8765 4321',
      status: 'ACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-24T10:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: null,
      deletedAt: null
    },
    {
      id: 'sup-2',
      name: 'Agroinsumos del Sur SpA',
      code: 'PROV-002',
      description: 'Sustratos',
      phone: '+56 9 1234 5678',
      status: 'INACTIVO',
      createdBy: 'admin',
      createdAt: '2026-09-24T11:00:00Z',
      updatedBy: 'admin',
      updatedAt: '2026-09-25T12:00:00Z',
      deletedBy: null,
      deletedAt: null
    },
    {
      id: 'sup-3',
      name: 'Comercial Eliminada Ltda.',
      code: 'PROV-003',
      description: 'Antiguo',
      phone: null,
      status: 'ELIMINADO',
      createdBy: 'admin',
      createdAt: '2026-09-20T10:00:00Z',
      updatedBy: null,
      updatedAt: null,
      deletedBy: 'admin',
      deletedAt: '2026-09-21T10:00:00Z'
    }
  ];

  beforeEach(async () => {
    mockSupplierService = {
      getSuppliers: vi.fn().mockReturnValue(of(mockSuppliers)),
      createSupplier: vi.fn(),
      updateSupplier: vi.fn(),
      deleteSupplier: vi.fn().mockReturnValue(of(null))
    };

    await TestBed.configureTestingModule({
      imports: [SuppliersComponent],
      providers: [
        { provide: SupplierService, useValue: mockSupplierService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SuppliersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('TEST-COMP-01: debe cargar proveedores ordenados por nombre A-Z y excluir eliminados en filtro por defecto', () => {
    expect(mockSupplierService.getSuppliers).toHaveBeenCalledTimes(1);

    const filtered = component.filteredSuppliers();
    // Excluye sup-3 (ELIMINADO) en filtro ACTIVE_INACTIVE
    expect(filtered.length).toBe(2);

    // Orden A-Z: "Agroinsumos del Sur SpA" antes de "Distribuidora del Norte S.A."
    expect(filtered[0].name).toBe('Agroinsumos del Sur SpA');
    expect(filtered[1].name).toBe('Distribuidora del Norte S.A.');

    const rows = fixture.debugElement.queryAll(By.css('tbody tr'));
    expect(rows.length).toBe(2);
  });

  it('TEST-COMP-02: debe filtrar por código o nombre de forma insensible a mayúsculas', () => {
    // Filtrar por coincidencia en código "PROV-001"
    component.searchTerm.set('prov-001');
    fixture.detectChanges();
    expect(component.filteredSuppliers().length).toBe(1);
    expect(component.filteredSuppliers()[0].code).toBe('PROV-001');

    // Filtrar por coincidencia parcial en nombre "agro"
    component.searchTerm.set('AGRO');
    fixture.detectChanges();
    expect(component.filteredSuppliers().length).toBe(1);
    expect(component.filteredSuppliers()[0].name).toBe('Agroinsumos del Sur SpA');
  });

  it('TEST-COMP-03: debe abrir modal de creación y mostrar error devuelto por backend en conflicto 409', () => {
    component.openCreateModal();
    fixture.detectChanges();

    expect(component.isFormModalOpen()).toBe(true);
    expect(component.isEditing()).toBe(false);

    component.supplierForm.setValue({
      name: 'Distribuidora del Norte S.A.',
      code: 'PROV-001',
      phone: '',
      description: '',
      status: 'ACTIVO'
    });

    const backendError = {
      error: {
        error: 'Conflict',
        message: 'Ya existe un proveedor activo o inactivo con el código especificado.'
      }
    };
    mockSupplierService.createSupplier.mockReturnValue(throwError(() => backendError));

    component.saveSupplier();
    fixture.detectChanges();

    expect(component.formError()).toBe('Ya existe un proveedor activo o inactivo con el código especificado.');
    expect(component.isFormModalOpen()).toBe(true);

    const alertEl = fixture.debugElement.query(By.css('.alert-danger'));
    expect(alertEl.nativeElement.textContent).toContain('Ya existe un proveedor activo o inactivo');
  });

  it('TEST-COMP-04: debe abrir modal de confirmación en eliminación y ejecutar soft delete al confirmar', () => {
    const target = mockSuppliers[0];
    component.openDeleteModal(target);
    fixture.detectChanges();

    expect(component.isConfirmModalOpen()).toBe(true);
    expect(component.supplierToDelete()).toEqual(target);

    // Confirmar eliminación
    component.confirmDelete();
    fixture.detectChanges();

    expect(mockSupplierService.deleteSupplier).toHaveBeenCalledWith('sup-1');
    expect(component.isConfirmModalOpen()).toBe(false);
    expect(component.suppliers().find(s => s.id === 'sup-1')).toBeUndefined();
  });

  it('TEST-COMP-06: debe abrir modal de auditoría al hacer clic en acción Auditoría', () => {
    const target = mockSuppliers[1]; // Agroinsumos con edición
    component.openAuditModal(target);
    fixture.detectChanges();

    expect(component.isAuditModalOpen()).toBe(true);
    expect(component.auditModalSubtitle()).toBe('PROV-002 - Agroinsumos del Sur SpA');
    expect(component.selectedAuditData()).toEqual({
      createdBy: 'admin',
      createdAt: '2026-09-24T11:00:00Z',
      updatedBy: 'admin',
      updatedAt: '2026-09-25T12:00:00Z',
      deletedBy: null,
      deletedAt: null
    });
  });

  // --- NUEVAS PRUEBAS PARA EL REQUERIMIENTO ADICIONAL ---

  it('TEST-COMP-07: en creación, el botón Guardar permanece habilitado aun con campos vacíos y valida al hacer clic', () => {
    component.openCreateModal();
    fixture.detectChanges();

    // En creación, hasFormChanges es falso (campos vacíos), pero Guardar NO debe estar deshabilitado
    expect(component.isEditing()).toBe(false);
    expect(component.hasFormChanges()).toBe(false);

    const submitBtn = fixture.debugElement.query(By.css('button[type="submit"]'));
    expect(submitBtn.nativeElement.disabled).toBe(false);

    // Al hacer clic en Guardar con formulario vacío, se ejecutan las validaciones y NO se llama al servicio
    component.saveSupplier();
    fixture.detectChanges();

    expect(component.supplierForm.invalid).toBe(true);
    expect(component.supplierForm.get('name')?.touched).toBe(true);
    expect(component.supplierForm.get('code')?.touched).toBe(true);
    expect(mockSupplierService.createSupplier).not.toHaveBeenCalled();
  });

  it('TEST-COMP-08: en edición, el botón Guardar está deshabilitado si no hay cambios y se habilita al modificar un campo', () => {
    const supplier = mockSuppliers[0];
    component.openEditModal(supplier);
    fixture.detectChanges();

    expect(component.isEditing()).toBe(true);
    // Sin cambios respecto al original
    expect(component.hasFormChanges()).toBe(false);

    const submitBtn = fixture.debugElement.query(By.css('button[type="submit"]'));
    expect(submitBtn.nativeElement.disabled).toBe(true);

    // Modificar teléfono
    component.supplierForm.patchValue({ phone: '+56 9 9999 0000' });
    fixture.detectChanges();

    expect(component.isFieldModified('phone')).toBe(true);
    expect(component.hasFormChanges()).toBe(true);
    expect(submitBtn.nativeElement.disabled).toBe(false);

    // Revertir teléfono al valor original cargado
    component.supplierForm.patchValue({ phone: supplier.phone });
    fixture.detectChanges();

    expect(component.isFieldModified('phone')).toBe(false);
    expect(component.hasFormChanges()).toBe(false);
    expect(submitBtn.nativeElement.disabled).toBe(true);
  });

  it('TEST-COMP-09: identifica visualmente campos modificados en edición y desaparece al restaurar original', () => {
    const supplier = mockSuppliers[0];
    component.openEditModal(supplier);
    fixture.detectChanges();

    expect(component.isFieldModified('name')).toBe(false);
    expect(fixture.debugElement.query(By.css('.field-modified-badge'))).toBeNull();

    // Modificar nombre
    component.supplierForm.patchValue({ name: 'Distribuidora del Norte Modificada' });
    fixture.detectChanges();

    expect(component.isFieldModified('name')).toBe(true);
    const badge = fixture.debugElement.query(By.css('.field-modified-badge'));
    expect(badge).not.toBeNull();
    expect(badge.nativeElement.textContent.trim()).toBe('Modificado');

    const nameInput = fixture.debugElement.query(By.css('#supplier-name'));
    expect(nameInput.nativeElement.classList.contains('is-modified')).toBe(true);

    // Restaurar nombre
    component.supplierForm.patchValue({ name: supplier.name });
    fixture.detectChanges();

    expect(component.isFieldModified('name')).toBe(false);
    expect(fixture.debugElement.query(By.css('.field-modified-badge'))).toBeNull();
    expect(nameInput.nativeElement.classList.contains('is-modified')).toBe(false);
  });

  it('TEST-COMP-10: confirmación al descartar cambios en creación y edición', () => {
    // 1. En creación sin cambios: cierra directo
    component.openCreateModal();
    fixture.detectChanges();
    component.attemptCloseForm();
    expect(component.isDiscardConfirmOpen()).toBe(false);
    expect(component.isFormModalOpen()).toBe(false);

    // 2. En creación con cambios: solicita confirmación
    component.openCreateModal();
    component.supplierForm.patchValue({ name: 'Algo escrito' });
    fixture.detectChanges();
    component.attemptCloseForm();
    expect(component.isDiscardConfirmOpen()).toBe(true);
    expect(component.isFormModalOpen()).toBe(true);

    // Continuar editando
    component.cancelDiscard();
    expect(component.isDiscardConfirmOpen()).toBe(false);
    expect(component.isFormModalOpen()).toBe(true);

    // Descartar cambios
    component.discardChanges();
    expect(component.isDiscardConfirmOpen()).toBe(false);
    expect(component.isFormModalOpen()).toBe(false);

    // 3. En edición con cambios: solicita confirmación
    component.openEditModal(mockSuppliers[0]);
    component.supplierForm.patchValue({ phone: '+56 9 0000 0000' });
    fixture.detectChanges();
    component.attemptCloseForm();
    expect(component.isDiscardConfirmOpen()).toBe(true);

    component.discardChanges();
    expect(component.isDiscardConfirmOpen()).toBe(false);
    expect(component.isFormModalOpen()).toBe(false);
  });

  it('TEST-COMP-11: filtro por estado cambia el listado y registros ELIMINADO solo muestran Auditoría', () => {
    // 1. Filtro ACTIVO
    component.setStatusFilter('ACTIVO');
    fixture.detectChanges();
    expect(component.selectedStatusFilter()).toBe('ACTIVO');
    expect(component.filteredSuppliers().length).toBe(1);
    expect(component.filteredSuppliers()[0].status).toBe('ACTIVO');

    // 2. Filtro INACTIVO
    component.setStatusFilter('INACTIVO');
    fixture.detectChanges();
    expect(component.filteredSuppliers().length).toBe(1);
    expect(component.filteredSuppliers()[0].status).toBe('INACTIVO');

    // 3. Filtro ELIMINADO
    component.setStatusFilter('ELIMINADO');
    fixture.detectChanges();
    expect(component.filteredSuppliers().length).toBe(1);
    expect(component.filteredSuppliers()[0].status).toBe('ELIMINADO');

    // Comprobar que registros ELIMINADO solo tienen botón de Auditoría (no Editar ni Eliminar)
    const editBtns = fixture.debugElement.queryAll(By.css('.btn-edit'));
    const deleteBtns = fixture.debugElement.queryAll(By.css('.btn-delete'));
    const auditBtns = fixture.debugElement.queryAll(By.css('.btn-audit'));

    expect(editBtns.length).toBe(0);
    expect(deleteBtns.length).toBe(0);
    expect(auditBtns.length).toBe(1);
  });

  it('TEST-COMP-12: el modal de formulario tiene closeOnBackdrop=false', () => {
    component.openCreateModal();
    fixture.detectChanges();

    const formModalDebug = fixture.debugElement.query(By.directive(ModalComponent));
    expect(formModalDebug).not.toBeNull();
    expect(formModalDebug.componentInstance.closeOnBackdrop()).toBe(false);
  });
});
