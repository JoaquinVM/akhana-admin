import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UsersComponent } from './users.component';
import { UserService } from '../../../core/user/user.service';
import { AuthService } from '../../../core/auth/auth.service';
import { User, PageResponse } from '../../../core/user/models/user.models';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { By } from '@angular/platform-browser';

describe('UsersComponent', () => {
  let component: UsersComponent;
  let fixture: ComponentFixture<UsersComponent>;
  let mockUserService: any;
  let mockAuthService: any;

  const mockUsersList: User[] = [
    {
      id: '11111111-1111-1111-1111-111111111111',
      fullName: 'Administrador General',
      username: 'admin',
      role: 'ADMIN',
      active: true,
      permissions: ['SALES_POS', 'SECURITY_USERS'],
      createdAt: '2026-01-01T10:00:00Z',
      createdBy: 'SYSTEM',
      updatedAt: '2026-02-01T12:00:00Z',
      updatedBy: 'admin',
      deletedAt: null,
      deletedBy: null
    },
    {
      id: '22222222-2222-2222-2222-222222222222',
      fullName: 'Vendedor Turno Mañana',
      username: 'seller',
      role: 'SELLER',
      active: true,
      permissions: ['SALES_POS'],
      createdAt: '2026-01-02T10:00:00Z',
      createdBy: 'admin',
      updatedAt: null,
      updatedBy: null,
      deletedAt: null,
      deletedBy: null
    }
  ];

  const mockPageResponse: PageResponse<User> = {
    content: mockUsersList,
    totalElements: 2,
    totalPages: 1,
    size: 10,
    number: 0
  };

  beforeEach(async () => {
    mockUserService = {
      getUsers: vi.fn().mockReturnValue(of(mockPageResponse)),
      getUserById: vi.fn().mockReturnValue(of(mockUsersList[0])),
      getAllPermissions: vi.fn().mockReturnValue(of(['SALES_POS', 'SECURITY_USERS'])),
      createUser: vi.fn().mockReturnValue(of(mockUsersList[0])),
      updateUser: vi.fn().mockReturnValue(of(mockUsersList[0])),
      deleteUser: vi.fn().mockReturnValue(of(void 0))
    };

    mockAuthService = {
      currentUser: signal({
        id: '11111111-1111-1111-1111-111111111111',
        username: 'admin',
        fullName: 'Administrador General',
        role: 'ADMIN',
        permissions: ['SECURITY_USERS']
      }),
      fetchCurrentUserProfile: vi.fn().mockReturnValue(of({})),
      hasPermission: vi.fn().mockReturnValue(true)
    };

    await TestBed.configureTestingModule({
      imports: [UsersComponent],
      providers: [
        { provide: UserService, useValue: mockUserService },
        { provide: AuthService, useValue: mockAuthService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(UsersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse y cargar la lista paginada de usuarios', () => {
    expect(component).toBeTruthy();
    expect(mockUserService.getUsers).toHaveBeenCalledWith('', 0, 10);
    expect(component.users().length).toBe(2);
    expect(component.totalElements()).toBe(2);

    const rows = fixture.debugElement.queryAll(By.css('.table-row'));
    expect(rows.length).toBe(2);
  });

  it('debe filtrar usuarios al buscar y limpiar el buscador', () => {
    component.onSearch({ target: { value: 'seller' } } as any);
    expect(mockUserService.getUsers).toHaveBeenCalledWith('seller', 0, 10);
    expect(component.searchTerm()).toBe('seller');

    component.clearSearch();
    expect(component.searchTerm()).toBe('');
    expect(mockUserService.getUsers).toHaveBeenCalledWith('', 0, 10);
  });

  describe('Modal de Creación', () => {
    it('debe abrir modal de creación y validar contraseñas coincidentes', () => {
      component.openCreateModal();
      expect(component.isCreateModalOpen()).toBe(true);

      component.createForm.patchValue({
        fullName: 'Nuevo Usuario',
        username: 'nuevo_user',
        password: 'password123',
        confirmPassword: 'password_distinto'
      });

      component.submitCreate();
      expect(component.createFormError()).toBe('Las contraseñas no coinciden.');
      expect(mockUserService.createUser).not.toHaveBeenCalled();
    });

    it('debe requerir al menos un permiso de acceso para crear usuario', () => {
      component.openCreateModal();
      component.selectedCreatePermissions.set([]); // Sin permisos

      component.createForm.patchValue({
        fullName: 'Nuevo Usuario',
        username: 'nuevo_user',
        password: 'password123',
        confirmPassword: 'password123'
      });

      component.submitCreate();
      expect(component.createFormError()).toBe('Debe asignar al menos un permiso de acceso al usuario.');
      expect(mockUserService.createUser).not.toHaveBeenCalled();
    });

    it('debe crear usuario exitosamente cuando el formulario es válido', () => {
      component.openCreateModal();
      component.selectedCreatePermissions.set(['SALES_POS']);

      component.createForm.patchValue({
        fullName: 'Nuevo Usuario',
        username: 'nuevo_user',
        password: 'password123',
        confirmPassword: 'password123'
      });

      component.submitCreate();
      expect(mockUserService.createUser).toHaveBeenCalledWith({
        fullName: 'Nuevo Usuario',
        username: 'nuevo_user',
        password: 'password123',
        permissions: ['SALES_POS']
      });
      expect(component.isCreateModalOpen()).toBe(false);
    });
  });

  describe('Modal de Edición y Detección Ergonómica de Cambios', () => {
    it('debe abrir modal de edición con username inmutable y sin cambios pendientes iniciales', () => {
      const user = mockUsersList[0];
      component.openEditModal(user);

      expect(component.isEditModalOpen()).toBe(true);
      expect(component.userToEdit()).toEqual(user);
      expect(component.editForm.value.fullName).toBe(user.fullName);
      expect(component.selectedEditPermissions()).toEqual(user.permissions);
      expect(component.hasFormChanges()).toBe(false);
      expect(component.isFieldModified('fullName')).toBe(false);
      expect(component.isFieldModified('permissions')).toBe(false);
    });

    it('debe detectar cambios cuando se modifica fullName y revertirse si vuelve al original', () => {
      const user = mockUsersList[0];
      component.openEditModal(user);

      // Modificar nombre
      component.editForm.patchValue({ fullName: 'Administrador Modificado' });
      expect(component.isFieldModified('fullName')).toBe(true);
      expect(component.hasFormChanges()).toBe(true);

      // Revertir nombre al original
      component.editForm.patchValue({ fullName: user.fullName });
      expect(component.isFieldModified('fullName')).toBe(false);
      expect(component.hasFormChanges()).toBe(false);
    });

    it('debe detectar cambios cuando se alteran los permisos y revertirse si vuelve al original', () => {
      const user = mockUsersList[0];
      component.openEditModal(user);

      // Añadir un permiso adicional
      component.selectedEditPermissions.set([...user.permissions, 'FINANCES_SUMMARY']);
      component.formVersion.update(v => v + 1);
      expect(component.isFieldModified('permissions')).toBe(true);
      expect(component.hasFormChanges()).toBe(true);

      // Restaurar exactamente los permisos iniciales
      component.selectedEditPermissions.set([...user.permissions]);
      component.formVersion.update(v => v + 1);
      expect(component.isFieldModified('permissions')).toBe(false);
      expect(component.hasFormChanges()).toBe(false);
    });

    it('debe advertir descarte si se intenta cerrar con modificaciones sin guardar', () => {
      const user = mockUsersList[0];
      component.openEditModal(user);

      component.editForm.patchValue({ fullName: 'Cambio Sin Guardar' });
      component.attemptCloseEditModal();

      expect(component.isDiscardConfirmOpen()).toBe(true);
      expect(component.isEditModalOpen()).toBe(true);

      component.confirmDiscard();
      expect(component.isDiscardConfirmOpen()).toBe(false);
      expect(component.isEditModalOpen()).toBe(false);
    });

    it('debe actualizar el usuario llamando al servicio con UpdateUserRequest', () => {
      const user = mockUsersList[1];
      component.openEditModal(user);

      component.editForm.patchValue({ fullName: 'Vendedor Turno Tarde' });
      component.selectedEditPermissions.set(['SALES_POS', 'CATALOG_PRODUCTS']);
      component.formVersion.update(v => v + 1);

      component.submitEdit();

      expect(mockUserService.updateUser).toHaveBeenCalledWith(user.id, {
        fullName: 'Vendedor Turno Tarde',
        permissions: ['SALES_POS', 'CATALOG_PRODUCTS']
      });
      expect(component.isEditModalOpen()).toBe(false);
    });
  });

  describe('Auditoría de 6 Campos', () => {
    it('debe abrir AuditModal con los 6 campos del usuario', () => {
      const user = mockUsersList[0];
      component.openAuditModal(user);

      expect(component.isAuditModalOpen()).toBe(true);
      expect(component.auditModalSubtitle()).toBe('Administrador General (admin)');
      expect(component.selectedAuditData()).toEqual({
        createdBy: 'SYSTEM',
        createdAt: '2026-01-01T10:00:00Z',
        updatedBy: 'admin',
        updatedAt: '2026-02-01T12:00:00Z',
        deletedBy: null,
        deletedAt: null
      });

      component.closeAuditModal();
      expect(component.isAuditModalOpen()).toBe(false);
      expect(component.selectedAuditData()).toBeNull();
    });
  });

  describe('Eliminación Lógica', () => {
    it('debe impedir auto-eliminación del usuario autenticado', () => {
      const loggedInUser = mockUsersList[0]; // username: 'admin'
      component.openDeleteModal(loggedInUser);

      expect(component.isDeleteModalOpen()).toBe(false);
      expect(component.errorMessage()).toBe('No puedes darte de baja a ti mismo del sistema.');
      expect(mockUserService.deleteUser).not.toHaveBeenCalled();
    });

    it('debe abrir confirmación y dar de baja a otro usuario', () => {
      const otherUser = mockUsersList[1]; // username: 'seller'
      component.openDeleteModal(otherUser);

      expect(component.isDeleteModalOpen()).toBe(true);
      expect(component.userToDelete()).toEqual(otherUser);

      component.confirmDelete();
      expect(mockUserService.deleteUser).toHaveBeenCalledWith(otherUser.id);
      expect(component.isDeleteModalOpen()).toBe(false);
    });
  });

  describe('Matriz de Permisos', () => {
    it('debe permitir seleccionar y deseleccionar todos los permisos globalmente', () => {
      component.openCreateModal();

      // Desmarcar todos
      component.toggleGlobalAll(true, { target: { checked: false } } as any);
      expect(component.selectedCreatePermissions().length).toBe(0);
      expect(component.isGlobalAllSelected(true)).toBe(false);

      // Seleccionar todos
      component.toggleGlobalAll(true, { target: { checked: true } } as any);
      expect(component.selectedCreatePermissions().length).toBe(component.allPermissionKeys.length);
      expect(component.isGlobalAllSelected(true)).toBe(true);
    });

    it('debe permitir seleccionar y deseleccionar un grupo completo de permisos', () => {
      component.openCreateModal();
      component.selectedCreatePermissions.set([]); // Partir de vacío

      // Marcar grupo Ventas
      component.toggleGroupAll('sales', true, { target: { checked: true } } as any);
      expect(component.isGroupAllSelected('sales', true)).toBe(true);
      expect(component.selectedCreatePermissions()).toContain('SALES_POS');
      expect(component.selectedCreatePermissions()).toContain('SALES_HISTORY');
      expect(component.selectedCreatePermissions()).toContain('SALES_CASH_HISTORY');

      // Desmarcar grupo Ventas
      component.toggleGroupAll('sales', true, { target: { checked: false } } as any);
      expect(component.isGroupAllSelected('sales', true)).toBe(false);
      expect(component.selectedCreatePermissions()).not.toContain('SALES_POS');
    });
  });
});
