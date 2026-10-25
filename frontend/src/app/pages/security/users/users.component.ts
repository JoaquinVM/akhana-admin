import { Component, OnInit, computed, inject, signal, DestroyRef } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { UserService } from '../../../core/user/user.service';
import { User, CreateUserRequest, UpdateUserRequest } from '../../../core/user/models/user.models';
import { AuthService } from '../../../core/auth/auth.service';
import { PERMISSION_GROUPS_CONFIG, PermissionGroupConfig } from '../../../core/navigation/navigation.config';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { ConfirmModalComponent } from '../../../shared/components/confirm-modal/confirm-modal.component';
import { AuditModalComponent } from '../../../shared/components/audit-modal/audit-modal.component';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    ReactiveFormsModule,
    ModalComponent,
    ConfirmModalComponent,
    AuditModalComponent
  ],
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.css']
})
export class UsersComponent implements OnInit {
  private readonly userService = inject(UserService);
  readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly permissionGroups: PermissionGroupConfig[] = PERMISSION_GROUPS_CONFIG;

  // Lista plana de todas las claves canónicas
  readonly allPermissionKeys: string[] = PERMISSION_GROUPS_CONFIG.flatMap(g => g.permissions.map(p => p.key));

  // Estados Reactivos Principales
  users = signal<User[]>([]);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal<string>('');

  // Paginación
  currentPage = signal<number>(0);
  pageSize = signal<number>(10);
  totalElements = signal<number>(0);
  totalPages = signal<number>(0);

  // Modal de Creación
  isCreateModalOpen = signal<boolean>(false);
  isCreating = signal<boolean>(false);
  createFormError = signal<string | null>(null);
  selectedCreatePermissions = signal<string[]>([]);

  createForm: FormGroup = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50), Validators.pattern(/^[a-zA-Z0-9._-]+$/)]],
    password: ['', [Validators.required, Validators.minLength(6)]],
    confirmPassword: ['', [Validators.required]]
  });

  // Modal de Edición
  isEditModalOpen = signal<boolean>(false);
  isUpdating = signal<boolean>(false);
  editFormError = signal<string | null>(null);
  userToEdit = signal<User | null>(null);
  selectedEditPermissions = signal<string[]>([]);
  originalFormValues = signal<{ fullName: string; permissions: string[] } | null>(null);
  formVersion = signal<number>(0);

  editForm: FormGroup = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]]
  });

  // Modal Confirmación de Descarte de Cambios
  isDiscardConfirmOpen = signal<boolean>(false);

  // Modal de Confirmación de Eliminación Lógica
  isDeleteModalOpen = signal<boolean>(false);
  isDeleting = signal<boolean>(false);
  userToDelete = signal<User | null>(null);
  deleteError = signal<string | null>(null);

  // Modal de Auditoría
  isAuditModalOpen = signal<boolean>(false);
  selectedAuditData = signal<Record<string, any> | null>(null);
  auditModalSubtitle = signal<string>('');

  // Detección ergonómica de cambios en edición (idéntico a ProductsComponent)
  hasFormChanges = computed<boolean>(() => {
    this.formVersion();
    const orig = this.originalFormValues();
    if (!orig) return false;

    return this.isFieldModified('fullName') || this.isFieldModified('permissions');
  });

  ngOnInit(): void {
    this.editForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.formVersion.update(v => v + 1);
      });

    this.loadUsers();
  }

  loadUsers(page = 0): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.currentPage.set(page);

    this.userService.getUsers(this.searchTerm(), page, this.pageSize()).subscribe({
      next: (response) => {
        this.users.set(response.content || []);
        this.totalElements.set(response.totalElements || 0);
        this.totalPages.set(response.totalPages || 0);
        this.isLoading.set(false);
      },
      error: () => {
        this.errorMessage.set('Error al cargar la lista de usuarios. Por favor, intente nuevamente.');
        this.isLoading.set(false);
      }
    });
  }

  onSearch(event: Event): void {
    const term = (event.target as HTMLInputElement).value;
    this.searchTerm.set(term);
    this.loadUsers(0);
  }

  clearSearch(): void {
    this.searchTerm.set('');
    this.loadUsers(0);
  }

  goToPage(page: number): void {
    if (page >= 0 && page < this.totalPages() && page !== this.currentPage()) {
      this.loadUsers(page);
    }
  }

  // --- Modal de Creación ---

  openCreateModal(): void {
    this.createForm.reset({
      fullName: '',
      username: '',
      password: '',
      confirmPassword: ''
    });
    this.selectedCreatePermissions.set([...this.allPermissionKeys]); // Por defecto todos seleccionados para conveniencia
    this.createFormError.set(null);
    this.isCreating.set(false);
    this.isCreateModalOpen.set(true);
  }

  closeCreateModal(): void {
    this.isCreateModalOpen.set(false);
    this.createFormError.set(null);
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    const { fullName, username, password, confirmPassword } = this.createForm.value;

    if (password !== confirmPassword) {
      this.createFormError.set('Las contraseñas no coinciden.');
      return;
    }

    const permissions = this.selectedCreatePermissions();
    if (permissions.length === 0) {
      this.createFormError.set('Debe asignar al menos un permiso de acceso al usuario.');
      return;
    }

    this.isCreating.set(true);
    this.createFormError.set(null);

    const request: CreateUserRequest = {
      fullName: fullName.trim(),
      username: username.trim(),
      password,
      permissions
    };

    this.userService.createUser(request).subscribe({
      next: () => {
        this.isCreating.set(false);
        this.closeCreateModal();
        this.loadUsers(this.currentPage());
      },
      error: (err) => {
        this.isCreating.set(false);
        this.createFormError.set(err.error?.message || 'Error al registrar el usuario. Verifique los datos.');
      }
    });
  }

  // --- Modal de Edición con Detección Ergonómica de Cambios ---

  openEditModal(user: User): void {
    this.userToEdit.set(user);
    this.editFormError.set(null);
    this.isUpdating.set(false);

    const userPerms = [...(user.permissions || [])];
    this.selectedEditPermissions.set(userPerms);

    this.editForm.reset({
      fullName: user.fullName
    });

    this.originalFormValues.set({
      fullName: user.fullName,
      permissions: userPerms
    });

    this.formVersion.update(v => v + 1);
    this.isEditModalOpen.set(true);
  }

  attemptCloseEditModal(): void {
    if (this.hasFormChanges()) {
      this.isDiscardConfirmOpen.set(true);
    } else {
      this.closeEditModal();
    }
  }

  confirmDiscard(): void {
    this.isDiscardConfirmOpen.set(false);
    this.closeEditModal();
  }

  cancelDiscard(): void {
    this.isDiscardConfirmOpen.set(false);
  }

  closeEditModal(): void {
    this.isEditModalOpen.set(false);
    this.userToEdit.set(null);
    this.originalFormValues.set(null);
    this.editFormError.set(null);
  }

  isFieldModified(fieldName: 'fullName' | 'permissions'): boolean {
    this.formVersion();
    const orig = this.originalFormValues();
    if (!orig) return false;

    if (fieldName === 'fullName') {
      const current = (this.editForm.get('fullName')?.value ?? '').toString().trim();
      const original = (orig.fullName ?? '').toString().trim();
      return current !== original;
    }

    if (fieldName === 'permissions') {
      const current = this.selectedEditPermissions().slice().sort();
      const original = orig.permissions.slice().sort();
      if (current.length !== original.length) return true;
      return current.some((val, idx) => val !== original[idx]);
    }

    return false;
  }

  submitEdit(): void {
    if (this.editForm.invalid || !this.hasFormChanges()) {
      this.editForm.markAllAsTouched();
      return;
    }

    const user = this.userToEdit();
    if (!user) return;

    const permissions = this.selectedEditPermissions();
    if (permissions.length === 0) {
      this.editFormError.set('Debe asignar al menos un permiso de acceso al usuario.');
      return;
    }

    this.isUpdating.set(true);
    this.editFormError.set(null);

    const request: UpdateUserRequest = {
      fullName: this.editForm.value.fullName.trim(),
      permissions
    };

    this.userService.updateUser(user.id, request).subscribe({
      next: (updatedUser) => {
        this.isUpdating.set(false);
        this.closeEditModal();
        // Si el usuario editado es el usuario en sesión, refrescar perfil para sincronizar Navbar
        if (this.authService.currentUser()?.id === user.id) {
          this.authService.fetchCurrentUserProfile().subscribe();
        }
        this.loadUsers(this.currentPage());
      },
      error: (err) => {
        this.isUpdating.set(false);
        this.editFormError.set(err.error?.message || 'Error al actualizar el usuario.');
      }
    });
  }

  // --- Modal de Confirmación de Baja Lógica ---

  openDeleteModal(user: User): void {
    const currentUsername = this.authService.currentUser()?.username;
    if (currentUsername && currentUsername.toLowerCase() === user.username.toLowerCase()) {
      this.errorMessage.set('No puedes darte de baja a ti mismo del sistema.');
      return;
    }

    this.userToDelete.set(user);
    this.deleteError.set(null);
    this.isDeleting.set(false);
    this.isDeleteModalOpen.set(true);
  }

  cancelDelete(): void {
    this.isDeleteModalOpen.set(false);
    this.userToDelete.set(null);
    this.deleteError.set(null);
  }

  confirmDelete(): void {
    const user = this.userToDelete();
    if (!user) return;

    this.isDeleting.set(true);
    this.deleteError.set(null);

    this.userService.deleteUser(user.id).subscribe({
      next: () => {
        this.isDeleting.set(false);
        this.isDeleteModalOpen.set(false);
        this.userToDelete.set(null);
        this.loadUsers(this.currentPage());
      },
      error: (err) => {
        this.isDeleting.set(false);
        this.deleteError.set(err.error?.message || 'Error al dar de baja al usuario.');
      }
    });
  }

  // --- Modal de Auditoría de 6 Campos ---

  openAuditModal(user: User): void {
    this.auditModalSubtitle.set(`${user.fullName} (${user.username})`);
    this.selectedAuditData.set({
      createdBy: user.createdBy,
      createdAt: user.createdAt,
      updatedBy: user.updatedBy,
      updatedAt: user.updatedAt,
      deletedBy: user.deletedBy,
      deletedAt: user.deletedAt
    });
    this.isAuditModalOpen.set(true);
  }

  closeAuditModal(): void {
    this.isAuditModalOpen.set(false);
    this.selectedAuditData.set(null);
  }

  // --- Lógica de Matriz de Permisos (Checkboxes Globales, Grupales e Individuales) ---

  isGlobalAllSelected(isCreate: boolean): boolean {
    const selected = isCreate ? this.selectedCreatePermissions() : this.selectedEditPermissions();
    return this.allPermissionKeys.length > 0 && selected.length === this.allPermissionKeys.length;
  }

  isGlobalIndeterminate(isCreate: boolean): boolean {
    const selected = isCreate ? this.selectedCreatePermissions() : this.selectedEditPermissions();
    return selected.length > 0 && selected.length < this.allPermissionKeys.length;
  }

  toggleGlobalAll(isCreate: boolean, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const newPerms = checked ? [...this.allPermissionKeys] : [];

    if (isCreate) {
      this.selectedCreatePermissions.set(newPerms);
    } else {
      this.selectedEditPermissions.set(newPerms);
      this.formVersion.update(v => v + 1);
    }
  }

  isGroupAllSelected(groupId: string, isCreate: boolean): boolean {
    const group = this.permissionGroups.find(g => g.id === groupId);
    if (!group) return false;
    const selected = isCreate ? this.selectedCreatePermissions() : this.selectedEditPermissions();
    return group.permissions.every(p => selected.includes(p.key));
  }

  isGroupIndeterminate(groupId: string, isCreate: boolean): boolean {
    const group = this.permissionGroups.find(g => g.id === groupId);
    if (!group) return false;
    const selected = isCreate ? this.selectedCreatePermissions() : this.selectedEditPermissions();
    const count = group.permissions.filter(p => selected.includes(p.key)).length;
    return count > 0 && count < group.permissions.length;
  }

  toggleGroupAll(groupId: string, isCreate: boolean, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const group = this.permissionGroups.find(g => g.id === groupId);
    if (!group) return;

    const groupKeys = group.permissions.map(p => p.key);
    const current = isCreate ? this.selectedCreatePermissions() : this.selectedEditPermissions();

    let updated: string[];
    if (checked) {
      updated = Array.from(new Set([...current, ...groupKeys]));
    } else {
      updated = current.filter(k => !groupKeys.includes(k));
    }

    if (isCreate) {
      this.selectedCreatePermissions.set(updated);
    } else {
      this.selectedEditPermissions.set(updated);
      this.formVersion.update(v => v + 1);
    }
  }

  isPermissionSelected(key: string, isCreate: boolean): boolean {
    const selected = isCreate ? this.selectedCreatePermissions() : this.selectedEditPermissions();
    return selected.includes(key);
  }

  togglePermission(key: string, isCreate: boolean, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const current = isCreate ? this.selectedCreatePermissions() : this.selectedEditPermissions();

    let updated: string[];
    if (checked) {
      updated = [...current, key];
    } else {
      updated = current.filter(k => k !== key);
    }

    if (isCreate) {
      this.selectedCreatePermissions.set(updated);
    } else {
      this.selectedEditPermissions.set(updated);
      this.formVersion.update(v => v + 1);
    }
  }
}
