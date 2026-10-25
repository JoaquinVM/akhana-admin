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
      role: 'ADMINISTRATOR',
      active: true,
      permissions: ['POS', 'FINANCES_TRANSACTIONS', 'USERS'],
      createdAt: '2026-01-01T10:00:00Z',
      createdBy: 'SYSTEM',
      updatedAt: '2026-02-01T12:00:00Z',
      updatedBy: 'admin',
      deletedAt: null,
      deletedBy: null
    },
    {
      id: '22222222-2222-2222-2222-222222222222',
      fullName: 'Operador Turno Mañana',
      username: 'operador',
      role: 'OPERATOR',
      active: true,
      permissions: ['POS', 'SALES_HISTORY'],
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
      createUser: vi.fn().mockReturnValue(of(mockUsersList[0])),
      updateUser: vi.fn().mockReturnValue(of(mockUsersList[0])),
      deleteUser: vi.fn().mockReturnValue(of(void 0))
    };

    mockAuthService = {
      currentUser: signal({
        id: '11111111-1111-1111-1111-111111111111',
        username: 'admin',
        fullName: 'Administrador General',
        role: 'ADMINISTRATOR',
        permissions: ['USERS']
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
    component.onSearch({ target: { value: 'operador' } } as any);
    expect(mockUserService.getUsers).toHaveBeenCalledWith('operador', 0, 10);
    expect(component.searchTerm()).toBe('operador');

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

    it('debe proponer el rol Operador por defecto y ofrecer ambos roles', () => {
      component.openCreateModal();
      fixture.detectChanges();

      expect(component.createForm.value.role).toBe('OPERATOR');
      const labels = fixture.debugElement
        .queryAll(By.css('.role-option-label'))
        .map(el => el.nativeElement.textContent.trim());
      expect(labels).toEqual(['Operador', 'Administrador']);
    });

    it('debe crear usuario exitosamente con el rol elegido', () => {
      component.openCreateModal();

      component.createForm.patchValue({
        role: 'ADMINISTRATOR',
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
        role: 'ADMINISTRATOR'
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
      expect(component.editForm.value.role).toBe('ADMINISTRATOR');
      expect(component.hasFormChanges()).toBe(false);
      expect(component.isFieldModified('fullName')).toBe(false);
      expect(component.isFieldModified('role')).toBe(false);
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

    it('debe detectar cambios cuando se cambia el rol y revertirse si vuelve al original', () => {
      const user = mockUsersList[0];
      component.openEditModal(user);

      component.editForm.patchValue({ role: 'OPERATOR' });
      expect(component.isFieldModified('role')).toBe(true);
      expect(component.hasFormChanges()).toBe(true);

      component.editForm.patchValue({ role: 'ADMINISTRATOR' });
      expect(component.isFieldModified('role')).toBe(false);
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

      component.editForm.patchValue({ fullName: 'Operador Turno Tarde', role: 'ADMINISTRATOR' });

      component.submitEdit();

      expect(mockUserService.updateUser).toHaveBeenCalledWith(user.id, {
        fullName: 'Operador Turno Tarde',
        role: 'ADMINISTRATOR'
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
      const otherUser = mockUsersList[1]; // username: 'operador'
      component.openDeleteModal(otherUser);

      expect(component.isDeleteModalOpen()).toBe(true);
      expect(component.userToDelete()).toEqual(otherUser);

      component.confirmDelete();
      expect(mockUserService.deleteUser).toHaveBeenCalledWith(otherUser.id);
      expect(component.isDeleteModalOpen()).toBe(false);
    });
  });

  it('debe mostrar el rol con su nombre en español en la tabla', () => {
    const badges = fixture.debugElement
      .queryAll(By.css('.table-row .role-badge'))
      .map(el => el.nativeElement.textContent.trim());
    expect(badges).toEqual(['Administrador', 'Operador']);
  });
});
