import { Component, OnInit, computed, inject, signal, DestroyRef } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { UserService } from '../../../core/user/user.service';
import { User, CreateUserRequest, UpdateUserRequest } from '../../../core/user/models/user.models';
import { AuthService } from '../../../core/auth/auth.service';
import { ROLE_OPTIONS, roleLabel } from '../../../core/navigation/navigation.config';
import { Role } from '../../../core/auth/models/auth.models';
import { ModalComponent } from '../../../shared/components/modal/modal.component';
import { ConfirmModalComponent } from '../../../shared/components/confirm-modal/confirm-modal.component';
import { AuditModalComponent } from '../../../shared/components/audit-modal/audit-modal.component';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { IconComponent } from '../../../shared/components/icon/icon.component';

@Component({
  selector: 'app-users',
  standalone: true,
  imports: [IconComponent, 
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
  private readonly toast = inject(ToastService);
  private readonly userService = inject(UserService);
  readonly authService = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly roleOptions = ROLE_OPTIONS;
  readonly roleLabel = roleLabel;

  // Estados Reactivos Principales
  users = signal<User[]>([]);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  searchTerm = signal<string>('');

  // Pagination
  currentPage = signal<number>(0);
  pageSize = signal<number>(10);
  totalElements = signal<number>(0);
  totalPages = signal<number>(0);

  // Create modal
  isCreateModalOpen = signal<boolean>(false);
  isCreating = signal<boolean>(false);
  createFormError = signal<string | null>(null);

  createForm: FormGroup = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50), Validators.pattern(/^[a-zA-Z0-9._-]+$/)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
    confirmPassword: ['', [Validators.required]],
    role: ['OPERATOR' as Role, [Validators.required]]
  });

  // Edit modal
  isEditModalOpen = signal<boolean>(false);
  isUpdating = signal<boolean>(false);
  editFormError = signal<string | null>(null);
  userToEdit = signal<User | null>(null);
  originalFormValues = signal<{ fullName: string; role: Role } | null>(null);
  formVersion = signal<number>(0);

  editForm: FormGroup = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    role: ['OPERATOR' as Role, [Validators.required]]
  });

  // Discard changes confirmation modal
  isDiscardConfirmOpen = signal<boolean>(false);

  // Logical delete confirmation modal
  isDeleteModalOpen = signal<boolean>(false);
  isDeleting = signal<boolean>(false);
  userToDelete = signal<User | null>(null);
  deleteError = signal<string | null>(null);

  // Audit modal
  isAuditModalOpen = signal<boolean>(false);
  selectedAuditData = signal<Record<string, any> | null>(null);
  auditModalSubtitle = signal<string>('');

  // Change detection while editing (same as ProductsComponent)
  hasFormChanges = computed<boolean>(() => {
    this.formVersion();
    const orig = this.originalFormValues();
    if (!orig) return false;

    return this.isFieldModified('fullName') || this.isFieldModified('role');
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

  // --- Create modal ---

  openCreateModal(): void {
    this.createForm.reset({
      fullName: '',
      username: '',
      password: '',
      confirmPassword: '',
      role: 'OPERATOR'
    });
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

    const { fullName, username, password, confirmPassword, role } = this.createForm.value;

    if (password !== confirmPassword) {
      this.createFormError.set('Las contraseñas no coinciden.');
      return;
    }

    this.isCreating.set(true);
    this.createFormError.set(null);

    const request: CreateUserRequest = {
      fullName: fullName.trim(),
      username: username.trim(),
      password,
      role
    };

    this.userService.createUser(request).subscribe({
      next: () => {
        this.toast.success('Usuario creado.');
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

  // --- Edit modal with change detection ---

  openEditModal(user: User): void {
    this.userToEdit.set(user);
    this.editFormError.set(null);
    this.isUpdating.set(false);

    this.editForm.reset({
      fullName: user.fullName,
      role: user.role
    });

    this.originalFormValues.set({
      fullName: user.fullName,
      role: user.role
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

  isFieldModified(fieldName: 'fullName' | 'role'): boolean {
    this.formVersion();
    const orig = this.originalFormValues();
    if (!orig) return false;

    if (fieldName === 'fullName') {
      const current = (this.editForm.get('fullName')?.value ?? '').toString().trim();
      const original = (orig.fullName ?? '').toString().trim();
      return current !== original;
    }

    if (fieldName === 'role') {
      return this.editForm.get('role')?.value !== orig.role;
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

    this.isUpdating.set(true);
    this.editFormError.set(null);

    const request: UpdateUserRequest = {
      fullName: this.editForm.value.fullName.trim(),
      role: this.editForm.value.role
    };

    this.userService.updateUser(user.id, request).subscribe({
      next: (updatedUser) => {
        this.toast.success(`Usuario ${updatedUser.username} actualizado.`);
        this.isUpdating.set(false);
        this.closeEditModal();
        // If the edited user is the signed-in one, refresh the profile (role and permissions) for the navbar
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

  // --- Logical delete confirmation modal ---

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
        this.toast.success('Usuario dado de baja.');
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

  // --- Audit modal (6 fields) ---

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
}
