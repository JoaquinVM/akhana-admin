import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PosService } from '../../services/pos.service';
import { AuthService } from '../../services/auth.service';
import { Sale, PaymentMethod } from '../../models/domain.model';
import { ConfirmModalComponent } from '../confirm-modal/confirm-modal.component';

@Component({
  selector: 'app-sales-history',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmModalComponent],
  template: `
    <div class="p-4 sm:p-6 max-w-7xl mx-auto space-y-6">
      
      <!-- Top Title & Search -->
      <div class="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between">
        <div>
          <h1 class="text-2xl font-bold font-headline text-[#1E2519] flex items-center gap-2">
            <span class="material-symbols-outlined text-[#3B6C2B] text-3xl">receipt_long</span>
            Consulta y Auditoría de Ventas
          </h1>
          <p class="text-xs sm:text-sm text-[#7D8774] mt-0.5">
            Historial de transacciones, edición de metadatos y anulación con trazabilidad
          </p>
        </div>

        <div class="flex items-center gap-2 w-full sm:w-auto">
          <div class="relative flex-1 sm:w-80">
            <span class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-[#7D8774]">
              <span class="material-symbols-outlined text-lg">search</span>
            </span>
            <input
              type="text"
              [(ngModel)]="searchQuery"
              placeholder="Buscar por orden, cliente, producto o código..."
              class="w-full pl-9 pr-4 py-2 text-xs sm:text-sm bg-white border border-[#D8D3C1] rounded-xl focus:ring-2 focus:ring-[#3B6C2B] focus:border-[#3B6C2B]"
            />
          </div>
        </div>
      </div>

      <!-- Filters Ribbon -->
      <div class="flex items-center gap-2 overflow-x-auto text-xs pb-1">
        <button
          (click)="statusFilter = 'ALL'"
          [ngClass]="statusFilter === 'ALL' ? 'bg-[#3B6C2B] text-white' : 'bg-white text-[#1E2519] border border-[#D8D3C1]'"
          class="px-3 py-1.5 rounded-lg font-semibold transition-colors">
          Todas ({{ allSales.length }})
        </button>
        <button
          (click)="statusFilter = 'COMPLETADA'"
          [ngClass]="statusFilter === 'COMPLETADA' ? 'bg-[#3B6C2B] text-white' : 'bg-white text-[#1E2519] border border-[#D8D3C1]'"
          class="px-3 py-1.5 rounded-lg font-semibold transition-colors flex items-center gap-1">
          <span class="w-2 h-2 rounded-full bg-[#7EB53F]"></span>
          Completadas ({{ completedCount }})
        </button>
        <button
          (click)="statusFilter = 'ANULADA'"
          [ngClass]="statusFilter === 'ANULADA' ? 'bg-[#C23B2A] text-white' : 'bg-white text-[#1E2519] border border-[#D8D3C1]'"
          class="px-3 py-1.5 rounded-lg font-semibold transition-colors flex items-center gap-1">
          <span class="w-2 h-2 rounded-full bg-[#C23B2A]"></span>
          Anuladas ({{ voidedCount }})
        </button>
      </div>

      <!-- Sales Table -->
      <div class="bg-white rounded-2xl border border-[#D8D3C1] shadow-xs overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full text-left text-xs sm:text-sm">
            <thead class="bg-[#FAF8F0] border-b border-[#D8D3C1] text-[#4C5544] uppercase tracking-wider font-semibold">
              <tr>
                <th class="p-3.5 pl-4">Orden / Fecha</th>
                <th class="p-3.5">Cliente</th>
                <th class="p-3.5">Cajero</th>
                <th class="p-3.5">Productos</th>
                <th class="p-3.5">Método de Pago</th>
                <th class="p-3.5">Total Neto</th>
                <th class="p-3.5">Estado</th>
                <th class="p-3.5 pr-4 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-[#EDE7D4]">
              <tr *ngFor="let sale of filteredSales" class="hover:bg-[#FAF8F0] transition-colors">
                
                <!-- Order & Date -->
                <td class="p-3.5 pl-4">
                  <span class="font-mono font-bold text-[#1E2519] block">{{ sale.orderNumber }}</span>
                  <span class="text-[11px] text-[#7D8774] font-mono">{{ sale.createdAt | date:'short' }}</span>
                </td>

                <!-- Customer -->
                <td class="p-3.5">
                  <span class="font-semibold text-[#1E2519] block">{{ sale.customerName }}</span>
                  <span *ngIf="sale.customerTaxId" class="text-[10px] text-[#7D8774] font-mono">NIT: {{ sale.customerTaxId }}</span>
                </td>

                <!-- Cashier -->
                <td class="p-3.5 text-[#4C5544]">
                  {{ sale.cashierName }}
                </td>

                <!-- Items list summary -->
                <td class="p-3.5">
                  <span class="font-semibold text-[#1E2519]">{{ sale.items.length }} ítems</span>
                  <div class="text-[10px] text-[#7D8774] max-w-[200px] truncate" [title]="getItemsList(sale)">
                    {{ getItemsList(sale) }}
                  </div>
                </td>

                <!-- Payment Method -->
                <td class="p-3.5">
                  <span
                    [ngClass]="sale.paymentMethod === 'CASH' ? 'bg-emerald-100 text-emerald-800' : (sale.paymentMethod === 'QR' ? 'bg-blue-100 text-blue-800' : 'bg-purple-100 text-purple-800')"
                    class="px-2 py-0.5 rounded-md font-bold text-[10px] uppercase">
                    {{ sale.paymentMethod }}
                  </span>
                </td>

                <!-- Total -->
                <td class="p-3.5 font-mono font-bold text-[#3B6C2B]">
                  Bs {{ sale.netTotal | number:'1.2-2' }}
                </td>

                <!-- Status & Void info -->
                <td class="p-3.5">
                  <span
                    [ngClass]="sale.status === 'COMPLETADA' ? 'bg-[#EBF5DE] text-[#1E3B0B] border border-[#8CBF41]/40' : 'bg-red-100 text-red-800 border border-red-300'"
                    class="px-2.5 py-0.5 rounded-full text-[11px] font-bold inline-flex items-center gap-1">
                    <span class="w-1.5 h-1.5 rounded-full" [ngClass]="sale.status === 'COMPLETADA' ? 'bg-[#7EB53F]' : 'bg-[#C23B2A]'"></span>
                    {{ sale.status }}
                  </span>
                  <div *ngIf="sale.status === 'ANULADA'" class="text-[10px] text-red-700 mt-1 max-w-[160px]">
                    <em>{{ sale.voidReason }}</em> ({{ sale.voidedBy }})
                  </div>
                </td>

                <!-- Actions -->
                <td class="p-3.5 pr-4 text-right">
                  <div class="flex items-center justify-end gap-1.5">
                    
                    <!-- View/Edit Details -->
                    <button
                      (click)="openEditModal(sale)"
                      class="p-1.5 rounded-lg text-[#4C5544] hover:text-[#1E2519] hover:bg-[#EDE7D4] transition-colors"
                      title="Editar datos de venta">
                      <span class="material-symbols-outlined text-base">edit</span>
                    </button>

                    <!-- Void Sale Button -->
                    <button
                      *ngIf="sale.status === 'COMPLETADA' && authService.isAdmin()"
                      (click)="openVoidModal(sale)"
                      class="p-1.5 rounded-lg text-red-600 hover:text-red-800 hover:bg-red-50 transition-colors"
                      title="Anular venta">
                      <span class="material-symbols-outlined text-base">cancel</span>
                    </button>

                  </div>
                </td>

              </tr>

              <tr *ngIf="filteredSales.length === 0">
                <td colspan="8" class="p-8 text-center text-[#7D8774]">
                  No se encontraron ventas registradas con el criterio de búsqueda.
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

    </div>

    <!-- Edit Sale Modal with Confirmation (lock.md requirement) -->
    <div *ngIf="saleToEdit" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
      <div class="bg-white rounded-2xl shadow-2xl border border-[#D8D3C1] max-w-md w-full overflow-hidden p-6 space-y-4">
        <div class="flex items-center justify-between border-b border-[#D8D3C1] pb-3">
          <h3 class="text-base font-bold font-headline text-[#1E2519]">Editar Datos de Venta {{ saleToEdit.orderNumber }}</h3>
          <button (click)="saleToEdit = null" class="text-[#7D8774] hover:text-[#1E2519]">
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>

        <div>
          <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Nombre del Cliente</label>
          <input
            type="text"
            [(ngModel)]="editCustomerName"
            class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-sm bg-[#FAF8F0]"
          />
        </div>

        <div>
          <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">NIT / CI del Cliente</label>
          <input
            type="text"
            [(ngModel)]="editCustomerTaxId"
            class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-sm bg-[#FAF8F0]"
          />
        </div>

        <div>
          <label class="block text-xs font-bold text-[#4C5544] uppercase mb-1">Método de Pago</label>
          <select [(ngModel)]="editPaymentMethod" class="w-full p-2.5 rounded-xl border border-[#D8D3C1] text-sm bg-[#FAF8F0]">
            <option value="CASH">Efectivo</option>
            <option value="QR">Simple QR</option>
            <option value="MIXED">Mixto</option>
          </select>
        </div>

        <div class="pt-3 flex justify-end gap-2 border-t border-[#D8D3C1]">
          <button (click)="saleToEdit = null" class="px-4 py-2 text-xs font-semibold rounded-lg bg-[#EDE7D4]">
            Cancelar
          </button>
          <button (click)="confirmEditSale()" class="px-4 py-2 text-xs font-semibold rounded-lg bg-[#3B6C2B] text-white">
            Guardar Cambios
          </button>
        </div>
      </div>
    </div>

    <!-- Confirm Dialog for Edit -->
    <app-confirm-modal
      [isOpen]="showConfirmEditModal"
      title="Confirmar Modificación de Venta"
      message="¿Confirma actualizar la información del cliente y método de pago de esta venta?"
      confirmText="Actualizar"
      cancelText="Cancelar"
      (confirmed)="executeEditSale()"
      (cancelled)="showConfirmEditModal = false">
    </app-confirm-modal>

    <!-- Void Sale Modal with Reason Requirement (lock.md requirement) -->
    <app-confirm-modal
      [isOpen]="showVoidModal"
      title="Anulación de Venta"
      message="La venta no se eliminará físicamente. Cambiará su estado a ANULADA y se restablecerá el stock de inventario."
      confirmText="Anular Venta"
      cancelText="Cancelar"
      [isDanger]="true"
      [requireInput]="true"
      inputLabel="Motivo Obligatorio de Anulación"
      inputPlaceholder="Ej: Devolución de producto, error en digitación..."
      (confirmed)="executeVoidSale($event)"
      (cancelled)="showVoidModal = false">
    </app-confirm-modal>
  `
})
export class SalesHistoryComponent implements OnInit {
  allSales: Sale[] = [];
  searchQuery = '';
  statusFilter: 'ALL' | 'COMPLETADA' | 'ANULADA' = 'ALL';

  // Edit modal
  saleToEdit: Sale | null = null;
  editCustomerName = '';
  editCustomerTaxId = '';
  editPaymentMethod: PaymentMethod = 'CASH';
  showConfirmEditModal = false;

  // Void modal
  saleToVoid: Sale | null = null;
  showVoidModal = false;

  constructor(
    private posService: PosService,
    public authService: AuthService
  ) {}

  ngOnInit(): void {
    this.posService.sales$.subscribe(sales => {
      this.allSales = sales;
    });
  }

  get completedCount(): number {
    return this.allSales.filter(s => s.status === 'COMPLETADA').length;
  }

  get voidedCount(): number {
    return this.allSales.filter(s => s.status === 'ANULADA').length;
  }

  get filteredSales(): Sale[] {
    let list = this.allSales;

    if (this.statusFilter !== 'ALL') {
      list = list.filter(s => s.status === this.statusFilter);
    }

    if (this.searchQuery.trim()) {
      const q = this.searchQuery.trim().toLowerCase();
      list = list.filter(s =>
        s.orderNumber.toLowerCase().includes(q) ||
        s.customerName.toLowerCase().includes(q) ||
        (s.customerTaxId && s.customerTaxId.toLowerCase().includes(q)) ||
        s.items.some(item =>
          item.productName.toLowerCase().includes(q) ||
          item.productCode.toLowerCase().includes(q) ||
          (item.barcode && item.barcode.toLowerCase().includes(q))
        )
      );
    }

    return list;
  }

  getItemsList(sale: Sale): string {
    return sale.items.map(i => `${i.productName} (${i.quantity}${i.saleType === 'weight' ? 'g' : 'u'})`).join(', ');
  }

  openEditModal(sale: Sale): void {
    this.saleToEdit = sale;
    this.editCustomerName = sale.customerName;
    this.editCustomerTaxId = sale.customerTaxId || '';
    this.editPaymentMethod = sale.paymentMethod;
  }

  confirmEditSale(): void {
    this.showConfirmEditModal = true;
  }

  executeEditSale(): void {
    if (!this.saleToEdit) return;
    this.posService.updateSale(
      this.saleToEdit.id,
      this.editCustomerName,
      this.editCustomerTaxId,
      this.editPaymentMethod
    );
    this.showConfirmEditModal = false;
    this.saleToEdit = null;
  }

  openVoidModal(sale: Sale): void {
    this.saleToVoid = sale;
    this.showVoidModal = true;
  }

  executeVoidSale(reason: string): void {
    if (!this.saleToVoid) return;
    try {
      this.posService.voidSale(this.saleToVoid.id, reason);
      this.showVoidModal = false;
      this.saleToVoid = null;
    } catch (e: any) {
      alert(e.message);
    }
  }
}
