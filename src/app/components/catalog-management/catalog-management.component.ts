import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CatalogService } from '../../services/catalog.service';
import { Category, Group, Provider, Tag } from '../../models/domain.model';
import { ConfirmModalComponent } from '../confirm-modal/confirm-modal.component';

export const COLOR_PALETTE = [
  '#3B6C2B', '#7EB53F', '#E5A823', '#C23B2A', '#2563EB', '#D97706',
  '#4E7A3E', '#8CBF41', '#9333EA', '#0D9488', '#E11D48', '#475569'
];

@Component({
  selector: 'app-catalog-management',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmModalComponent],
  template: `
    <div class="p-4 sm:p-6 max-w-7xl mx-auto space-y-6">
      
      <!-- Top Title -->
      <div>
        <h1 class="text-2xl font-bold font-headline text-[#1E2519] flex items-center gap-2">
          <span class="material-symbols-outlined text-[#3B6C2B] text-3xl">category</span>
          Gestión de Catálogo y Entidades Maestras
        </h1>
        <p class="text-xs sm:text-sm text-[#7D8774] mt-0.5">
          Administración de Categorías, Proveedores, Grupos y Etiquetas
        </p>
      </div>

      <!-- Navigation Tabs -->
      <div class="flex items-center gap-2 border-b border-[#D8D3C1] pb-2">
        <button
          (click)="activeTab = 'CATEGORIES'"
          [ngClass]="activeTab === 'CATEGORIES' ? 'bg-[#3B6C2B] text-white' : 'bg-white text-[#1E2519] border border-[#D8D3C1]'"
          class="px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all flex items-center gap-1.5">
          <span class="material-symbols-outlined text-base">folder</span>
          Categorías ({{ categories.length }})
        </button>
        <button
          (click)="activeTab = 'PROVIDERS'"
          [ngClass]="activeTab === 'PROVIDERS' ? 'bg-[#3B6C2B] text-white' : 'bg-white text-[#1E2519] border border-[#D8D3C1]'"
          class="px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all flex items-center gap-1.5">
          <span class="material-symbols-outlined text-base">local_shipping</span>
          Proveedores ({{ providers.length }})
        </button>
        <button
          (click)="activeTab = 'GROUPS'"
          [ngClass]="activeTab === 'GROUPS' ? 'bg-[#3B6C2B] text-white' : 'bg-white text-[#1E2519] border border-[#D8D3C1]'"
          class="px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all flex items-center gap-1.5">
          <span class="material-symbols-outlined text-base">grid_view</span>
          Grupos ({{ groups.length }})
        </button>
        <button
          (click)="activeTab = 'TAGS'"
          [ngClass]="activeTab === 'TAGS' ? 'bg-[#3B6C2B] text-white' : 'bg-white text-[#1E2519] border border-[#D8D3C1]'"
          class="px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition-all flex items-center gap-1.5">
          <span class="material-symbols-outlined text-base">label</span>
          Etiquetas ({{ tags.length }})
        </button>
      </div>

      <!-- ================= TAB: CATEGORIES ================= -->
      <div *ngIf="activeTab === 'CATEGORIES'" class="space-y-4">
        <div class="flex justify-between items-center">
          <input
            type="text"
            [(ngModel)]="searchCategoryQuery"
            placeholder="Buscar categoría por nombre..."
            class="px-3.5 py-2 text-xs sm:text-sm bg-white border border-[#D8D3C1] rounded-xl w-64"
          />
          <button
            (click)="openCategoryModal()"
            class="px-4 py-2 bg-[#3B6C2B] text-white rounded-xl text-xs sm:text-sm font-bold flex items-center gap-1">
            <span class="material-symbols-outlined text-base">add</span>
            Nueva Categoría
          </button>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
          <div *ngFor="let cat of filteredCategories" class="bg-white p-4 rounded-xl border border-[#D8D3C1] shadow-2xs flex items-center justify-between">
            <div class="flex items-center gap-3">
              <span class="w-4 h-4 rounded-full" [style.backgroundColor]="cat.color"></span>
              <div>
                <h4 class="text-sm font-bold text-[#1E2519]">{{ cat.name }}</h4>
                <p class="text-xs text-[#7D8774]">{{ cat.description || 'Sin descripción' }}</p>
              </div>
            </div>
            <div class="flex items-center gap-1">
              <button (click)="editCategory(cat)" class="p-1 text-[#4C5544] hover:text-[#1E2519]">
                <span class="material-symbols-outlined text-base">edit</span>
              </button>
              <button (click)="deleteCategory(cat)" class="p-1 text-red-600 hover:text-red-800">
                <span class="material-symbols-outlined text-base">delete</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- ================= TAB: PROVIDERS ================= -->
      <div *ngIf="activeTab === 'PROVIDERS'" class="space-y-4">
        <div class="flex justify-between items-center">
          <input
            type="text"
            [(ngModel)]="searchProviderQuery"
            placeholder="Buscar proveedor por código o nombre..."
            class="px-3.5 py-2 text-xs sm:text-sm bg-white border border-[#D8D3C1] rounded-xl w-64"
          />
          <button
            (click)="openProviderModal()"
            class="px-4 py-2 bg-[#3B6C2B] text-white rounded-xl text-xs sm:text-sm font-bold flex items-center gap-1">
            <span class="material-symbols-outlined text-base">add</span>
            Nuevo Proveedor
          </button>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
          <div *ngFor="let prov of filteredProviders" class="bg-white p-4 rounded-xl border border-[#D8D3C1] shadow-2xs flex items-start justify-between">
            <div>
              <span class="text-[10px] font-mono font-bold bg-[#EDE7D4] text-[#4C5544] px-1.5 py-0.5 rounded-sm">
                {{ prov.code }}
              </span>
              <h4 class="text-sm font-bold text-[#1E2519] mt-1">{{ prov.name }}</h4>
              <p class="text-xs text-[#7D8774]">{{ prov.phone || 'Sin teléfono' }}</p>
            </div>
            <div class="flex items-center gap-1">
              <button (click)="editProvider(prov)" class="p-1 text-[#4C5544] hover:text-[#1E2519]">
                <span class="material-symbols-outlined text-base">edit</span>
              </button>
              <button (click)="deleteProvider(prov)" class="p-1 text-red-600 hover:text-red-800">
                <span class="material-symbols-outlined text-base">delete</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- ================= TAB: GROUPS ================= -->
      <div *ngIf="activeTab === 'GROUPS'" class="space-y-4">
        <div class="flex justify-between items-center">
          <input
            type="text"
            [(ngModel)]="searchGroupQuery"
            placeholder="Buscar grupo por nombre..."
            class="px-3.5 py-2 text-xs sm:text-sm bg-white border border-[#D8D3C1] rounded-xl w-64"
          />
          <button
            (click)="openGroupModal()"
            class="px-4 py-2 bg-[#3B6C2B] text-white rounded-xl text-xs sm:text-sm font-bold flex items-center gap-1">
            <span class="material-symbols-outlined text-base">add</span>
            Nuevo Grupo
          </button>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
          <div *ngFor="let grp of filteredGroups" class="bg-white p-4 rounded-xl border border-[#D8D3C1] shadow-2xs flex items-center justify-between">
            <div>
              <h4 class="text-sm font-bold text-[#1E2519]">{{ grp.name }}</h4>
              <p class="text-xs text-[#7D8774]">{{ grp.description || 'Sin descripción' }}</p>
            </div>
            <div class="flex items-center gap-1">
              <button (click)="editGroup(grp)" class="p-1 text-[#4C5544] hover:text-[#1E2519]">
                <span class="material-symbols-outlined text-base">edit</span>
              </button>
              <button (click)="deleteGroup(grp)" class="p-1 text-red-600 hover:text-red-800">
                <span class="material-symbols-outlined text-base">delete</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- ================= TAB: TAGS ================= -->
      <div *ngIf="activeTab === 'TAGS'" class="space-y-4">
        <div class="flex justify-between items-center">
          <input
            type="text"
            [(ngModel)]="searchTagQuery"
            placeholder="Buscar etiqueta por nombre..."
            class="px-3.5 py-2 text-xs sm:text-sm bg-white border border-[#D8D3C1] rounded-xl w-64"
          />
          <button
            (click)="openTagModal()"
            class="px-4 py-2 bg-[#3B6C2B] text-white rounded-xl text-xs sm:text-sm font-bold flex items-center gap-1">
            <span class="material-symbols-outlined text-base">add</span>
            Nueva Etiqueta
          </button>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
          <div *ngFor="let tag of filteredTags" class="bg-white p-4 rounded-xl border border-[#D8D3C1] shadow-2xs flex items-center justify-between">
            <div class="flex items-center gap-3">
              <span class="w-3.5 h-3.5 rounded-full" [style.backgroundColor]="tag.color"></span>
              <div>
                <h4 class="text-sm font-bold text-[#1E2519]">{{ tag.name }}</h4>
                <p class="text-xs text-[#7D8774]">{{ tag.description || 'Sin descripción' }}</p>
              </div>
            </div>
            <div class="flex items-center gap-1">
              <button (click)="editTag(tag)" class="p-1 text-[#4C5544] hover:text-[#1E2519]">
                <span class="material-symbols-outlined text-base">edit</span>
              </button>
              <button (click)="deleteTag(tag)" class="p-1 text-red-600 hover:text-red-800">
                <span class="material-symbols-outlined text-base">delete</span>
              </button>
            </div>
          </div>
        </div>
      </div>

    </div>

    <!-- Generic Modal for Categories, Providers, Groups, Tags -->
    <div *ngIf="showEntityModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
      <div class="bg-white rounded-2xl shadow-2xl border border-[#D8D3C1] max-w-md w-full overflow-hidden p-6 space-y-4">
        <div class="flex justify-between items-center border-b border-[#D8D3C1] pb-3">
          <h3 class="text-base font-bold text-[#1E2519]">{{ modalTitle }}</h3>
          <button (click)="showEntityModal = false" class="text-[#7D8774] hover:text-[#1E2519]">
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>

        <!-- Code field if Provider -->
        <div *ngIf="activeTab === 'PROVIDERS'">
          <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Código del Proveedor *</label>
          <input
            type="text"
            [(ngModel)]="entityCode"
            placeholder="Ej: PRV-005"
            class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-xs font-mono uppercase bg-[#FAF8F0]"
          />
        </div>

        <!-- Name field (Required for all) -->
        <div>
          <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Nombre *</label>
          <input
            type="text"
            [(ngModel)]="entityName"
            placeholder="Nombre descriptivo..."
            class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-sm bg-[#FAF8F0]"
          />
        </div>

        <!-- Description (Optional) -->
        <div>
          <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Descripción (Opcional)</label>
          <textarea
            [(ngModel)]="entityDesc"
            rows="2"
            class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-sm bg-[#FAF8F0]"
          ></textarea>
        </div>

        <!-- Phone if Provider -->
        <div *ngIf="activeTab === 'PROVIDERS'">
          <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Teléfono (Opcional)</label>
          <input
            type="text"
            [(ngModel)]="entityPhone"
            placeholder="+591 7..."
            class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-xs bg-[#FAF8F0]"
          />
        </div>

        <!-- Color Palette for Categories and Tags (lock.md requirement) -->
        <div *ngIf="activeTab === 'CATEGORIES' || activeTab === 'TAGS'">
          <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1.5">Paleta de Colores *</label>
          <div class="flex items-center gap-2 flex-wrap mb-2">
            <button
              type="button"
              *ngFor="let c of colorPalette"
              (click)="entityColor = c"
              [style.backgroundColor]="c"
              [ngClass]="entityColor === c ? 'ring-2 ring-offset-2 ring-black scale-110' : ''"
              class="w-7 h-7 rounded-full transition-transform cursor-pointer shadow-xs"></button>
          </div>
          <div class="flex items-center gap-2">
            <span class="text-xs text-[#7D8774]">Color seleccionado:</span>
            <span class="w-4 h-4 rounded-full inline-block" [style.backgroundColor]="entityColor"></span>
            <span class="text-xs font-mono font-bold">{{ entityColor }}</span>
          </div>
        </div>

        <div class="pt-3 flex justify-end gap-2 border-t border-[#D8D3C1]">
          <button (click)="showEntityModal = false" class="px-4 py-2 text-xs font-semibold rounded-lg bg-[#EDE7D4]">
            Cancelar
          </button>
          <button
            (click)="triggerConfirmSaveModal()"
            [disabled]="!entityName.trim() || (activeTab === 'PROVIDERS' && !entityCode.trim())"
            class="px-5 py-2 text-xs font-bold rounded-lg bg-[#3B6C2B] text-white disabled:opacity-50">
            {{ isEditing ? 'Guardar Cambios' : 'Crear' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Confirm Save Modal -->
    <app-confirm-modal
      [isOpen]="showConfirmSaveModal"
      [title]="isEditing ? 'Confirmar Edición' : 'Confirmar Creación'"
      [message]="'¿Confirma guardar los datos de ' + entityName + '?'"
      confirmText="Guardar"
      cancelText="Cancelar"
      (confirmed)="executeSaveEntity()"
      (cancelled)="showConfirmSaveModal = false">
    </app-confirm-modal>

    <!-- Confirm Delete Modal -->
    <app-confirm-modal
      [isOpen]="showConfirmDeleteModal"
      title="Confirmar Eliminación"
      [message]="'¿Está seguro de eliminar ' + entityName + '? Esta acción no se puede deshacer.'"
      confirmText="Sí, Eliminar"
      cancelText="Cancelar"
      [isDanger]="true"
      (confirmed)="executeDeleteEntity()"
      (cancelled)="showConfirmDeleteModal = false">
    </app-confirm-modal>
  `
})
export class CatalogManagementComponent implements OnInit {
  activeTab: 'CATEGORIES' | 'PROVIDERS' | 'GROUPS' | 'TAGS' = 'CATEGORIES';

  categories: Category[] = [];
  providers: Provider[] = [];
  groups: Group[] = [];
  tags: Tag[] = [];

  searchCategoryQuery = '';
  searchProviderQuery = '';
  searchGroupQuery = '';
  searchTagQuery = '';

  colorPalette = COLOR_PALETTE;

  // Modal State
  showEntityModal = false;
  modalTitle = '';
  isEditing = false;
  editingId = '';

  entityName = '';
  entityDesc = '';
  entityCode = '';
  entityPhone = '';
  entityColor = '#3B6C2B';

  showConfirmSaveModal = false;
  showConfirmDeleteModal = false;

  constructor(private catalogService: CatalogService) {}

  ngOnInit(): void {
    this.catalogService.categories$.subscribe(c => this.categories = c);
    this.catalogService.providers$.subscribe(p => this.providers = p);
    this.catalogService.groups$.subscribe(g => this.groups = g);
    this.catalogService.tags$.subscribe(t => this.tags = t);
  }

  get filteredCategories(): Category[] {
    if (!this.searchCategoryQuery.trim()) return this.categories;
    const q = this.searchCategoryQuery.trim().toLowerCase();
    return this.categories.filter(c => c.name.toLowerCase().includes(q));
  }

  get filteredProviders(): Provider[] {
    if (!this.searchProviderQuery.trim()) return this.providers;
    const q = this.searchProviderQuery.trim().toLowerCase();
    return this.providers.filter(p => p.name.toLowerCase().includes(q) || p.code.toLowerCase().includes(q));
  }

  get filteredGroups(): Group[] {
    if (!this.searchGroupQuery.trim()) return this.groups;
    const q = this.searchGroupQuery.trim().toLowerCase();
    return this.groups.filter(g => g.name.toLowerCase().includes(q));
  }

  get filteredTags(): Tag[] {
    if (!this.searchTagQuery.trim()) return this.tags;
    const q = this.searchTagQuery.trim().toLowerCase();
    return this.tags.filter(t => t.name.toLowerCase().includes(q));
  }

  openCategoryModal(): void {
    this.isEditing = false;
    this.modalTitle = 'Nueva Categoría';
    this.entityName = '';
    this.entityDesc = '';
    this.entityColor = '#E5A823';
    this.showEntityModal = true;
  }

  editCategory(cat: Category): void {
    this.isEditing = true;
    this.editingId = cat.id;
    this.modalTitle = 'Editar Categoría';
    this.entityName = cat.name;
    this.entityDesc = cat.description || '';
    this.entityColor = cat.color;
    this.showEntityModal = true;
  }

  deleteCategory(cat: Category): void {
    this.editingId = cat.id;
    this.entityName = cat.name;
    this.showConfirmDeleteModal = true;
  }

  openProviderModal(): void {
    this.isEditing = false;
    this.modalTitle = 'Nuevo Proveedor';
    this.entityCode = '';
    this.entityName = '';
    this.entityDesc = '';
    this.entityPhone = '';
    this.showEntityModal = true;
  }

  editProvider(p: Provider): void {
    this.isEditing = true;
    this.editingId = p.id;
    this.modalTitle = 'Editar Proveedor';
    this.entityCode = p.code;
    this.entityName = p.name;
    this.entityDesc = p.description || '';
    this.entityPhone = p.phone || '';
    this.showEntityModal = true;
  }

  deleteProvider(p: Provider): void {
    this.editingId = p.id;
    this.entityName = p.name;
    this.showConfirmDeleteModal = true;
  }

  openGroupModal(): void {
    this.isEditing = false;
    this.modalTitle = 'Nuevo Grupo';
    this.entityName = '';
    this.entityDesc = '';
    this.showEntityModal = true;
  }

  editGroup(g: Group): void {
    this.isEditing = true;
    this.editingId = g.id;
    this.modalTitle = 'Editar Grupo';
    this.entityName = g.name;
    this.entityDesc = g.description || '';
    this.showEntityModal = true;
  }

  deleteGroup(g: Group): void {
    this.editingId = g.id;
    this.entityName = g.name;
    this.showConfirmDeleteModal = true;
  }

  openTagModal(): void {
    this.isEditing = false;
    this.modalTitle = 'Nueva Etiqueta';
    this.entityName = '';
    this.entityDesc = '';
    this.entityColor = '#3B6C2B';
    this.showEntityModal = true;
  }

  editTag(t: Tag): void {
    this.isEditing = true;
    this.editingId = t.id;
    this.modalTitle = 'Editar Etiqueta';
    this.entityName = t.name;
    this.entityDesc = t.description || '';
    this.entityColor = t.color;
    this.showEntityModal = true;
  }

  deleteTag(t: Tag): void {
    this.editingId = t.id;
    this.entityName = t.name;
    this.showConfirmDeleteModal = true;
  }

  triggerConfirmSaveModal(): void {
    this.showConfirmSaveModal = true;
  }

  executeSaveEntity(): void {
    try {
      if (this.activeTab === 'CATEGORIES') {
        if (this.isEditing) {
          this.catalogService.updateCategory(this.editingId, { name: this.entityName, description: this.entityDesc, color: this.entityColor });
        } else {
          this.catalogService.addCategory({ name: this.entityName, description: this.entityDesc, color: this.entityColor });
        }
      } else if (this.activeTab === 'PROVIDERS') {
        if (this.isEditing) {
          this.catalogService.updateProvider(this.editingId, { code: this.entityCode, name: this.entityName, description: this.entityDesc, phone: this.entityPhone });
        } else {
          this.catalogService.addProvider({ code: this.entityCode, name: this.entityName, description: this.entityDesc, phone: this.entityPhone });
        }
      } else if (this.activeTab === 'GROUPS') {
        if (this.isEditing) {
          this.catalogService.updateGroup(this.editingId, { name: this.entityName, description: this.entityDesc });
        } else {
          this.catalogService.addGroup({ name: this.entityName, description: this.entityDesc });
        }
      } else if (this.activeTab === 'TAGS') {
        if (this.isEditing) {
          this.catalogService.updateTag(this.editingId, { name: this.entityName, description: this.entityDesc, color: this.entityColor });
        } else {
          this.catalogService.addTag({ name: this.entityName, description: this.entityDesc, color: this.entityColor });
        }
      }

      this.showConfirmSaveModal = false;
      this.showEntityModal = false;
    } catch (e: any) {
      alert('Error: ' + e.message);
      this.showConfirmSaveModal = false;
    }
  }

  executeDeleteEntity(): void {
    if (this.activeTab === 'CATEGORIES') {
      this.catalogService.deleteCategory(this.editingId);
    } else if (this.activeTab === 'PROVIDERS') {
      this.catalogService.deleteProvider(this.editingId);
    } else if (this.activeTab === 'GROUPS') {
      this.catalogService.deleteGroup(this.editingId);
    } else if (this.activeTab === 'TAGS') {
      this.catalogService.deleteTag(this.editingId);
    }
    this.showConfirmDeleteModal = false;
  }
}
