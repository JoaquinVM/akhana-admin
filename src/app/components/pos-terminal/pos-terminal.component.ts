import { Component, HostListener, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PosService } from '../../services/pos.service';
import { CatalogService } from '../../services/catalog.service';
import { CashRegisterService } from '../../services/cash-register.service';
import { SheetsSyncService } from '../../services/sheets-sync.service';
import { Category, Product, SaleItem, PaymentMethod, Sale } from '../../models/domain.model';
import { ConfirmModalComponent } from '../confirm-modal/confirm-modal.component';

@Component({
  selector: 'app-pos-terminal',
  standalone: true,
  imports: [CommonModule, FormsModule, ConfirmModalComponent],
  template: `
    <div class="flex-1 flex flex-col lg:flex-row h-[calc(100vh-4rem)] overflow-hidden bg-[#F7F4E9]">
      
      <!-- ================= LEFT: CATALOG WORKSPACE (65%) ================= -->
      <section class="flex-1 flex flex-col min-w-0 border-r border-[#D8D3C1] overflow-hidden">
        
        <!-- Search & Barcode Scanner Bar -->
        <div class="p-3 sm:p-4 bg-[#FAF8F0] border-b border-[#D8D3C1] flex flex-col sm:flex-row gap-3 items-center justify-between shadow-xs shrink-0">
          <div class="relative flex-1 w-full">
            <span class="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-[#7D8774]">
              <span class="material-symbols-outlined text-xl">barcode_scanner</span>
            </span>
            <input
              #searchInput
              type="text"
              [(ngModel)]="searchQuery"
              (keyup.enter)="handleSearchOrScan()"
              placeholder="Escanear código de barras [Lector] o buscar producto por nombre/código..."
              class="w-full pl-11 pr-24 py-2.5 bg-white border border-[#D8D3C1] rounded-xl text-sm font-medium text-[#1E2519] placeholder:text-[#7D8774] focus:outline-hidden focus:ring-2 focus:ring-[#3B6C2B] focus:border-[#3B6C2B] transition-all shadow-inner"
            />
            <!-- Scanner Laser Animation -->
            <div class="absolute inset-y-0 right-3 flex items-center gap-1">
              <span class="text-[10px] font-mono font-bold uppercase tracking-wider bg-[#EBF5DE] text-[#1E3B0B] px-2 py-0.5 rounded-md border border-[#8CBF41]/30">
                Lector Activo
              </span>
            </div>
          </div>

          <div class="flex items-center gap-2 w-full sm:w-auto justify-end">
            <button
              (click)="clearSearch()"
              *ngIf="searchQuery"
              class="px-3 py-2 text-xs font-semibold text-[#4C5544] hover:text-[#1E2519] bg-[#EDE7D4] rounded-lg transition-colors">
              Limpiar
            </button>
            <span class="text-xs font-semibold text-[#4C5544] bg-[#EDE7D4] px-3 py-2 rounded-lg">
              {{ filteredProducts.length }} productos
            </span>
          </div>
        </div>

        <!-- Grouped Quick-Add System (12 Top Sellers in 3 Categories) -->
        <div class="p-3 sm:p-4 bg-[#FAF8F0] border-b border-[#D8D3C1] shrink-0">
          <div class="flex items-center justify-between mb-2">
            <h3 class="text-xs font-bold text-[#1E2519] uppercase tracking-wider flex items-center gap-1.5">
              <span class="material-symbols-outlined text-[#E5A823] text-base">bolt</span>
              Acceso Rápido • 12 Más Vendidos (Agrupados)
            </h3>
            <span class="text-[11px] text-[#4C5544]">Clic para agregar directamente</span>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-3 gap-2.5">
            <!-- Group 1: Panadería & Desayuno (4 items) -->
            <div class="bg-white rounded-xl p-2.5 border border-[#D8D3C1] shadow-2xs flex flex-col justify-between">
              <div class="flex items-center justify-between mb-1.5">
                <span class="text-[11px] font-bold text-[#1E2519] uppercase tracking-wide flex items-center gap-1">
                  🥖 Panadería & Desayuno
                </span>
                <span class="text-[9px] font-bold bg-[#FEF6DC] text-[#6B4C00] px-1.5 py-0.5 rounded-sm">4 ítems</span>
              </div>
              <div class="grid grid-cols-2 gap-1.5">
                <button
                  *ngFor="let p of quickGroup1"
                  (click)="onSelectProduct(p)"
                  class="p-2 rounded-lg bg-[#FAF8F0] hover:bg-[#EBF5DE] border border-[#D8D3C1] hover:border-[#7EB53F] transition-all text-left flex flex-col justify-between group">
                  <span class="text-[11px] font-semibold text-[#1E2519] line-clamp-1 group-hover:text-[#3B6C2B]">{{ p.name }}</span>
                  <div class="flex items-center justify-between mt-1">
                    <span class="text-[10px] text-[#7D8774] font-mono">{{ p.code }}</span>
                    <span class="text-[11px] font-mono font-bold bg-[#E5A823] text-amber-950 px-1.5 py-0.2 rounded-md">
                      Bs {{ p.salePrice | number:'1.2-2' }}
                    </span>
                  </div>
                </button>
              </div>
            </div>

            <!-- Group 2: Charcutería & Fiambres (4 items) -->
            <div class="bg-white rounded-xl p-2.5 border border-[#D8D3C1] shadow-2xs flex flex-col justify-between">
              <div class="flex items-center justify-between mb-1.5">
                <span class="text-[11px] font-bold text-[#1E2519] uppercase tracking-wide flex items-center gap-1">
                  🥩 Charcutería & Fiambres
                </span>
                <span class="text-[9px] font-bold bg-[#FEF6DC] text-[#6B4C00] px-1.5 py-0.5 rounded-sm">4 ítems</span>
              </div>
              <div class="grid grid-cols-2 gap-1.5">
                <button
                  *ngFor="let p of quickGroup2"
                  (click)="onSelectProduct(p)"
                  class="p-2 rounded-lg bg-[#FAF8F0] hover:bg-[#EBF5DE] border border-[#D8D3C1] hover:border-[#7EB53F] transition-all text-left flex flex-col justify-between group">
                  <span class="text-[11px] font-semibold text-[#1E2519] line-clamp-1 group-hover:text-[#3B6C2B]">{{ p.name }}</span>
                  <div class="flex items-center justify-between mt-1">
                    <span class="text-[10px] text-[#7D8774] font-mono">{{ p.saleType === 'weight' ? '100g' : 'c/u' }}</span>
                    <span class="text-[11px] font-mono font-bold bg-[#E5A823] text-amber-950 px-1.5 py-0.2 rounded-md">
                      Bs {{ p.salePrice | number:'1.2-2' }}
                    </span>
                  </div>
                </button>
              </div>
            </div>

            <!-- Group 3: Bebidas & Snacks (4 items) -->
            <div class="bg-white rounded-xl p-2.5 border border-[#D8D3C1] shadow-2xs flex flex-col justify-between">
              <div class="flex items-center justify-between mb-1.5">
                <span class="text-[11px] font-bold text-[#1E2519] uppercase tracking-wide flex items-center gap-1">
                  ☕ Bebidas & Snacks
                </span>
                <span class="text-[9px] font-bold bg-[#FEF6DC] text-[#6B4C00] px-1.5 py-0.5 rounded-sm">4 ítems</span>
              </div>
              <div class="grid grid-cols-2 gap-1.5">
                <button
                  *ngFor="let p of quickGroup3"
                  (click)="onSelectProduct(p)"
                  class="p-2 rounded-lg bg-[#FAF8F0] hover:bg-[#EBF5DE] border border-[#D8D3C1] hover:border-[#7EB53F] transition-all text-left flex flex-col justify-between group">
                  <span class="text-[11px] font-semibold text-[#1E2519] line-clamp-1 group-hover:text-[#3B6C2B]">{{ p.name }}</span>
                  <div class="flex items-center justify-between mt-1">
                    <span class="text-[10px] text-[#7D8774] font-mono">{{ p.code }}</span>
                    <span class="text-[11px] font-mono font-bold bg-[#E5A823] text-amber-950 px-1.5 py-0.2 rounded-md">
                      Bs {{ p.salePrice | number:'1.2-2' }}
                    </span>
                  </div>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Category Tabs -->
        <div class="px-3 sm:px-4 py-2 bg-[#EDE7D4] border-b border-[#D8D3C1] flex items-center gap-1.5 overflow-x-auto shrink-0">
          <button
            (click)="selectedCategory = 'ALL'"
            [ngClass]="selectedCategory === 'ALL' ? 'bg-[#3B6C2B] text-white shadow-xs' : 'bg-[#FAF8F0] text-[#1E2519] hover:bg-white'"
            class="px-3 py-1.5 rounded-lg text-xs font-bold whitespace-nowrap transition-all">
            Todos ({{ allProducts.length }})
          </button>
          <button
            *ngFor="let cat of categories"
            (click)="selectedCategory = cat.id"
            [ngClass]="selectedCategory === cat.id ? 'bg-[#3B6C2B] text-white shadow-xs' : 'bg-[#FAF8F0] text-[#1E2519] hover:bg-white'"
            class="px-3 py-1.5 rounded-lg text-xs font-semibold whitespace-nowrap transition-all flex items-center gap-1.5">
            <span class="w-2 h-2 rounded-full" [style.backgroundColor]="cat.color"></span>
            {{ cat.name }}
          </button>
        </div>

        <!-- Product Grid -->
        <div class="flex-1 p-3 sm:p-4 overflow-y-auto">
          <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-3 xl:grid-cols-4 gap-3">
            <div
              *ngFor="let prod of filteredProducts"
              (click)="onSelectProduct(prod)"
              class="bg-white rounded-xl border border-[#D8D3C1] p-3 shadow-2xs hover:shadow-md hover:border-[#3B6C2B] transition-all cursor-pointer flex flex-col justify-between group relative overflow-hidden">
              
              <!-- Pesable or Stock Badge -->
              <div class="flex items-center justify-between mb-1.5">
                <span *ngIf="prod.saleType === 'weight'" class="text-[10px] font-bold bg-[#EBF5DE] text-[#1E3B0B] px-1.5 py-0.5 rounded-md flex items-center gap-0.5">
                  <span class="material-symbols-outlined text-xs">scale</span>
                  Pesable
                </span>
                <span *ngIf="prod.saleType === 'unit'" class="text-[10px] font-bold bg-[#EDE7D4] text-[#4C5544] px-1.5 py-0.5 rounded-md">
                  Unidad
                </span>
                <span class="text-[10px] font-mono text-[#7D8774]">{{ prod.code }}</span>
              </div>

              <!-- Product Info -->
              <div class="my-1">
                <h4 class="text-xs sm:text-sm font-bold text-[#1E2519] leading-snug group-hover:text-[#3B6C2B] transition-colors line-clamp-2">
                  {{ prod.name }}
                </h4>
                <p class="text-[11px] text-[#7D8774] mt-0.5 font-mono">
                  {{ prod.barcode || 'Sin código barras' }}
                </p>
              </div>

              <!-- Price & Add Trigger -->
              <div class="mt-2 pt-2 border-t border-[#D8D3C1]/50 flex items-center justify-between">
                <div>
                  <span class="text-[10px] text-[#7D8774] block leading-none">
                    {{ prod.saleType === 'weight' ? 'Por 100g' : 'Precio Unit.' }}
                  </span>
                  <span class="text-sm sm:text-base font-bold font-mono text-[#3B6C2B]">
                    Bs {{ prod.salePrice | number:'1.2-2' }}
                  </span>
                </div>
                <div class="w-7 h-7 rounded-lg bg-[#FAF8F0] group-hover:bg-[#3B6C2B] group-hover:text-white flex items-center justify-center text-[#3B6C2B] transition-colors shadow-2xs">
                  <span class="material-symbols-outlined text-base">add</span>
                </div>
              </div>
            </div>
          </div>

          <div *ngIf="filteredProducts.length === 0" class="text-center py-12 text-[#7D8774]">
            <span class="material-symbols-outlined text-4xl mb-2 text-[#D8D3C1]">search_off</span>
            <p class="text-sm font-medium">No se encontraron productos con el filtro actual.</p>
          </div>
        </div>

      </section>

      <!-- ================= RIGHT: SHOPPING CART (35%) ================= -->
      <section class="w-full lg:w-[460px] xl:w-[500px] flex flex-col bg-[#FAF8F0] h-full shadow-lg shrink-0 overflow-hidden">
        
        <!-- Cart Header -->
        <div class="p-3 sm:p-4 bg-white border-b border-[#D8D3C1] flex items-center justify-between shadow-2xs shrink-0">
          <div class="flex items-center gap-2">
            <span class="material-symbols-outlined text-[#3B6C2B] text-xl">shopping_cart</span>
            <div>
              <div class="flex items-center gap-2">
                <span class="text-sm font-bold font-headline text-[#1E2519]">Carrito de Venta</span>
                <span class="text-[11px] font-mono font-bold bg-[#E5A823] text-amber-950 px-2 py-0.5 rounded-md border border-amber-300">
                  #ORD-8492
                </span>
              </div>
              <span class="text-[11px] text-[#7D8774]">
                {{ cartItems.length }} {{ cartItems.length === 1 ? 'producto seleccionado' : 'productos seleccionados' }}
              </span>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <button
              (click)="confirmClearCart()"
              [disabled]="cartItems.length === 0"
              class="text-xs text-red-600 hover:text-red-800 disabled:opacity-30 disabled:cursor-not-allowed font-semibold p-1.5 rounded-lg hover:bg-red-50 transition-colors flex items-center gap-1">
              <span class="material-symbols-outlined text-base">delete_sweep</span>
              Vaciar
            </button>
          </div>
        </div>

        <!-- Customer Selector -->
        <div class="px-4 py-2 bg-[#EDE7D4]/60 border-b border-[#D8D3C1] flex items-center justify-between text-xs shrink-0">
          <div class="flex items-center gap-1.5 text-[#1E2519] font-medium flex-1">
            <span class="material-symbols-outlined text-base text-[#7D8774]">person</span>
            <input
              type="text"
              [(ngModel)]="customerName"
              (change)="onCustomerNameChange()"
              placeholder="Nombre del Cliente..."
              class="bg-transparent border-b border-dashed border-[#7D8774] text-xs font-semibold focus:outline-hidden focus:border-[#3B6C2B] py-0.5 px-1"
            />
          </div>
          <span class="text-[11px] text-[#7D8774] font-mono">Caja #01</span>
        </div>

        <!-- Scrollable Cart Items List (POPULATED WITH SELECTED PRODUCTS) -->
        <div class="flex-1 p-3 space-y-2.5 overflow-y-auto">
          
          <div
            *ngFor="let item of cartItems; let i = index"
            class="bg-white rounded-xl p-3 border border-[#D8D3C1] shadow-2xs flex flex-col gap-2 hover:border-[#3B6C2B]/50 transition-colors">
            
            <!-- Top Line: Name & Total Price -->
            <div class="flex items-start justify-between gap-2">
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-1.5">
                  <span class="text-xs font-bold text-[#1E2519] truncate">{{ item.productName }}</span>
                  <span *ngIf="item.saleType === 'weight'" class="text-[9px] font-bold bg-[#EBF5DE] text-[#1E3B0B] px-1.5 py-0.2 rounded-sm shrink-0">
                    ⚖️ Balanza
                  </span>
                </div>
                <span class="text-[10px] text-[#7D8774] font-mono block">
                  Base: Bs {{ item.unitPrice | number:'1.2-2' }} {{ item.saleType === 'weight' ? '/100g' : 'c/u' }}
                </span>
              </div>

              <!-- Item Total Price (Strikethrough if discounted) -->
              <div class="text-right shrink-0">
                <span *ngIf="item.discount > 0" class="text-[10px] text-[#7D8774] line-through block font-mono">
                  Bs {{ item.grossSubtotal | number:'1.2-2' }}
                </span>
                <span class="text-sm font-bold font-mono text-[#3B6C2B]">
                  Bs {{ item.finalSubtotal | number:'1.2-2' }}
                </span>
              </div>
            </div>

            <!-- Bottom Line: Editable Quantity / Weight & Editable Discount (NO CHIPS) -->
            <div class="flex items-center justify-between gap-2 pt-1 border-t border-[#EDE7D4]">
              
              <!-- Quantity / Weight Input -->
              <div class="flex items-center gap-1.5">
                <!-- Unit Stepper -->
                <div *ngIf="item.saleType === 'unit'" class="flex items-center">
                  <button
                    type="button"
                    (click)="changeQty(i, -1)"
                    class="w-6 h-6 bg-[#EDE7D4] hover:bg-[#DED6C1] rounded-l-md font-bold text-xs flex items-center justify-center text-[#1E2519] transition-colors">-</button>
                  <input
                    type="number"
                    min="1"
                    max="999"
                    [ngModel]="item.quantity"
                    (ngModelChange)="onQtyInput(i, $event)"
                    class="w-10 h-6 text-center font-bold font-mono text-xs border-y border-[#D8D3C1] bg-white focus:outline-hidden"
                  />
                  <button
                    type="button"
                    (click)="changeQty(i, 1)"
                    class="w-6 h-6 bg-[#EDE7D4] hover:bg-[#DED6C1] rounded-r-md font-bold text-xs flex items-center justify-center text-[#1E2519] transition-colors">+</button>
                </div>

                <!-- Weight Grams Input -->
                <div *ngIf="item.saleType === 'weight'" class="flex items-center gap-1 bg-[#FAF8F0] px-2 py-0.5 rounded-lg border border-[#D8D3C1]">
                  <span class="text-[10px] font-semibold text-[#7D8774]">Peso:</span>
                  <input
                    type="number"
                    min="10"
                    step="25"
                    [ngModel]="item.quantity"
                    (ngModelChange)="onQtyInput(i, $event)"
                    class="w-12 text-center font-bold font-mono text-xs bg-white border border-[#D8D3C1] rounded-sm py-0.5 focus:ring-1 focus:ring-[#3B6C2B]"
                  />
                  <span class="text-[10px] font-bold text-[#1E2519]">g</span>
                </div>
              </div>

              <!-- Direct Editable Discount Input (Warm Amber Golden Box) -->
              <div class="flex items-center gap-1.5">
                <div class="flex items-center gap-1 bg-[#FEF6DC] px-2 py-1 rounded-lg border border-[#F59E0B]">
                  <span class="text-[10px] font-bold text-[#6B4C00]">Desc: Bs</span>
                  <input
                    type="number"
                    min="0"
                    step="0.5"
                    max="5"
                    [ngModel]="item.discount"
                    (ngModelChange)="onDiscountInput(i, $event)"
                    class="w-12 text-center font-bold font-mono text-xs bg-white border border-amber-300 rounded-sm py-0.5 text-amber-950 focus:ring-1 focus:ring-[#E5A823]"
                  />
                </div>

                <!-- Delete Item Button -->
                <button
                  type="button"
                  (click)="removeItem(i)"
                  class="w-7 h-7 rounded-lg text-red-500 hover:text-red-700 hover:bg-red-50 flex items-center justify-center transition-colors">
                  <span class="material-symbols-outlined text-base">close</span>
                </button>
              </div>

            </div>

          </div>

          <!-- Empty State -->
          <div *ngIf="cartItems.length === 0" class="text-center py-12 text-[#7D8774]">
            <span class="material-symbols-outlined text-4xl mb-2 text-[#D8D3C1]">remove_shopping_cart</span>
            <p class="text-sm font-semibold">El carrito está vacío</p>
            <p class="text-xs text-[#7D8774] mt-0.5">Escanea o haz clic en los productos para agregarlos.</p>
          </div>

        </div>

        <!-- ================= CART FOOTER: DISCOUNTS, TOTALS & PAYMENT ================= -->
        <div class="p-3 sm:p-4 bg-white border-t border-[#D8D3C1] space-y-3 shrink-0 shadow-lg">
          
          <!-- Global Ticket Discount Card (Warm Golden Amber Styling) -->
          <div class="p-2.5 rounded-xl bg-[#FEF6DC] border border-[#F59E0B] flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="material-symbols-outlined text-[#E5A823] text-lg">loyalty</span>
              <div>
                <span class="text-xs font-bold text-[#6B4C00] block">Descuento Global del Ticket</span>
                <span class="text-[10px] text-amber-800">Límite máx: Bs 20.00</span>
              </div>
            </div>
            <div class="flex items-center gap-1.5">
              <span class="text-xs font-bold font-mono text-[#6B4C00]">Bs</span>
              <input
                type="number"
                min="0"
                step="1"
                max="20"
                [(ngModel)]="globalDiscount"
                (ngModelChange)="onGlobalDiscountChange($event)"
                class="w-16 text-center font-bold font-mono text-sm bg-white border border-[#F59E0B] rounded-lg py-1 text-amber-950 focus:ring-2 focus:ring-[#E5A823]"
              />
            </div>
          </div>

          <!-- Financial Breakdown -->
          <div class="space-y-1 text-xs">
            <div class="flex justify-between text-[#4C5544]">
              <span>Subtotal Bruto</span>
              <span class="font-mono font-semibold">Bs {{ totals.grossSubtotal | number:'1.2-2' }}</span>
            </div>
            <div class="flex justify-between text-amber-700 font-semibold" *ngIf="totals.totalDiscounts > 0">
              <span>Total Descuentos (Ítems + Global)</span>
              <span class="font-mono">-Bs {{ totals.totalDiscounts | number:'1.2-2' }}</span>
            </div>
            <div class="flex justify-between items-baseline pt-1 border-t border-[#EDE7D4]">
              <div>
                <span class="text-sm font-extrabold font-headline text-[#1E2519]">TOTAL A PAGAR</span>
                <span class="text-[10px] text-[#7D8774] block">(Incluye 13% IVA Bolivia: Bs {{ totals.tax13 | number:'1.2-2' }})</span>
              </div>
              <span class="text-2xl sm:text-3xl font-extrabold font-mono text-[#3B6C2B]">
                Bs {{ totals.netTotal | number:'1.2-2' }}
              </span>
            </div>
          </div>

          <!-- Bolivian Payment Methods (Efectivo, Simple QR, Mixto) -->
          <div>
            <div class="grid grid-cols-3 gap-1.5 mb-2">
              <button
                type="button"
                (click)="paymentMethod = 'CASH'"
                [ngClass]="paymentMethod === 'CASH' ? 'bg-[#3B6C2B] text-white font-bold shadow-xs' : 'bg-[#FAF8F0] text-[#1E2519] border border-[#D8D3C1]'"
                class="py-2 px-1 rounded-xl text-xs flex items-center justify-center gap-1 transition-all">
                <span class="material-symbols-outlined text-base">payments</span>
                Efectivo
              </button>
              <button
                type="button"
                (click)="paymentMethod = 'QR'"
                [ngClass]="paymentMethod === 'QR' ? 'bg-blue-700 text-white font-bold shadow-xs' : 'bg-[#FAF8F0] text-[#1E2519] border border-[#D8D3C1]'"
                class="py-2 px-1 rounded-xl text-xs flex items-center justify-center gap-1 transition-all">
                <span class="material-symbols-outlined text-base">qr_code_2</span>
                Simple QR
              </button>
              <button
                type="button"
                (click)="paymentMethod = 'MIXED'"
                [ngClass]="paymentMethod === 'MIXED' ? 'bg-[#5A6F50] text-white font-bold shadow-xs' : 'bg-[#FAF8F0] text-[#1E2519] border border-[#D8D3C1]'"
                class="py-2 px-1 rounded-xl text-xs flex items-center justify-center gap-1 transition-all">
                <span class="material-symbols-outlined text-base">splitscreen</span>
                Mixto
              </button>
            </div>

            <!-- Cash Payment Details & Bolivian Quick Bill Helper -->
            <div *ngIf="paymentMethod === 'CASH'" class="space-y-2 bg-[#FAF8F0] p-2.5 rounded-xl border border-[#D8D3C1]">
              <div class="flex items-center justify-between gap-2">
                <span class="text-xs font-semibold text-[#1E2519]">Monto Recibido:</span>
                <div class="flex items-center gap-1">
                  <span class="text-xs font-bold font-mono">Bs</span>
                  <input
                    type="number"
                    min="0"
                    step="5"
                    [(ngModel)]="cashReceived"
                    (ngModelChange)="calculateChange()"
                    class="w-24 text-right font-bold font-mono text-sm px-2 py-1 bg-white border border-[#D8D3C1] rounded-lg focus:ring-2 focus:ring-[#3B6C2B]"
                  />
                </div>
              </div>

              <!-- Quick Bolivian Bill Buttons (+10, +20, +50, +100, Exacto) -->
              <div class="flex items-center gap-1 overflow-x-auto">
                <button
                  type="button"
                  (click)="setExactCash()"
                  class="px-2 py-1 text-[10px] font-bold bg-white border border-[#D8D3C1] rounded-md hover:bg-[#EBF5DE] transition-colors">
                  Exacto
                </button>
                <button
                  type="button"
                  (click)="addCashBill(10)"
                  class="px-2 py-1 text-[10px] font-bold bg-white border border-[#D8D3C1] rounded-md hover:bg-[#FEF6DC] transition-colors">
                  +Bs 10
                </button>
                <button
                  type="button"
                  (click)="addCashBill(20)"
                  class="px-2 py-1 text-[10px] font-bold bg-white border border-[#D8D3C1] rounded-md hover:bg-[#FEF6DC] transition-colors">
                  +Bs 20
                </button>
                <button
                  type="button"
                  (click)="addCashBill(50)"
                  class="px-2 py-1 text-[10px] font-bold bg-white border border-[#D8D3C1] rounded-md hover:bg-[#FEF6DC] transition-colors">
                  +Bs 50
                </button>
                <button
                  type="button"
                  (click)="addCashBill(100)"
                  class="px-2 py-1 text-[10px] font-bold bg-white border border-[#D8D3C1] rounded-md hover:bg-[#FEF6DC] transition-colors">
                  +Bs 100
                </button>
              </div>

              <div class="flex justify-between items-center text-xs pt-1 border-t border-[#D8D3C1]/50">
                <span class="font-semibold text-[#4C5544]">Cambio / Vuelto:</span>
                <span class="font-mono font-extrabold text-sm" [ngClass]="changeAmount >= 0 ? 'text-[#3B6C2B]' : 'text-red-600'">
                  Bs {{ changeAmount | number:'1.2-2' }}
                </span>
              </div>
            </div>

            <!-- Simple QR Payment Details -->
            <div *ngIf="paymentMethod === 'QR'" class="p-3 bg-blue-50/80 rounded-xl border border-blue-200 flex items-center gap-3">
              <div class="w-14 h-14 bg-white p-1 rounded-lg border border-blue-300 flex items-center justify-center shrink-0">
                <span class="material-symbols-outlined text-4xl text-blue-700">qr_code_scanner</span>
              </div>
              <div class="flex-1 text-xs">
                <div class="font-bold text-blue-900 flex items-center gap-1">
                  <span>Simple QR Interbancario</span>
                  <span class="text-[9px] bg-blue-200 text-blue-900 px-1 rounded-sm">ASOBAN</span>
                </div>
                <p class="text-blue-700 text-[11px] mt-0.5">El cliente escanea el QR desde su banco por <strong>Bs {{ totals.netTotal | number:'1.2-2' }}</strong>.</p>
              </div>
            </div>

            <!-- Mixed Split Payment Details -->
            <div *ngIf="paymentMethod === 'MIXED'" class="space-y-2 bg-[#FAF8F0] p-2.5 rounded-xl border border-[#D8D3C1]">
              <div class="flex items-center justify-between text-xs">
                <span class="font-semibold text-[#1E2519]">Monto en Efectivo:</span>
                <div class="flex items-center gap-1">
                  <span class="font-mono font-bold">Bs</span>
                  <input
                    type="number"
                    min="0"
                    [(ngModel)]="cashReceived"
                    (ngModelChange)="calculateChange()"
                    class="w-20 text-right font-mono font-bold text-xs p-1 bg-white border border-[#D8D3C1] rounded-md"
                  />
                </div>
              </div>
              <div class="flex items-center justify-between text-xs">
                <span class="font-semibold text-blue-800">Monto Simple QR:</span>
                <div class="flex items-center gap-1">
                  <span class="font-mono font-bold text-blue-800">Bs</span>
                  <input
                    type="number"
                    min="0"
                    [(ngModel)]="qrReceived"
                    (ngModelChange)="calculateChange()"
                    class="w-20 text-right font-mono font-bold text-xs p-1 bg-white border border-blue-300 rounded-md"
                  />
                </div>
              </div>
            </div>

          </div>

          <!-- Bottom Main Confirmation Button -->
          <button
            type="button"
            (click)="triggerConfirmSaleModal()"
            [disabled]="cartItems.length === 0 || totals.netTotal <= 0"
            class="w-full py-3.5 px-4 rounded-xl font-bold font-headline text-base bg-[#3B6C2B] hover:bg-[#4E7A3E] text-white shadow-md hover:shadow-lg transition-all disabled:opacity-40 disabled:cursor-not-allowed flex items-center justify-center gap-2">
            <span class="material-symbols-outlined text-xl">check_circle</span>
            <span>Confirmar Venta (F9) • Bs {{ totals.netTotal | number:'1.2-2' }}</span>
          </button>

        </div>

      </section>

    </div>

    <!-- Confirmation Modal for Sale Creation (Lock.md Requirement) -->
    <app-confirm-modal
      [isOpen]="showConfirmSaleModal"
      title="Confirmar Registro de Venta"
      [message]="'¿Desea registrar y confirmar la venta a nombre de ' + customerName + ' por un total de Bs ' + (totals.netTotal | number:'1.2-2') + '?'"
      confirmText="Confirmar e Imprimir"
      cancelText="Volver"
      (confirmed)="executeSale()"
      (cancelled)="showConfirmSaleModal = false">
    </app-confirm-modal>

    <!-- Confirmation Modal for Clear Cart -->
    <app-confirm-modal
      [isOpen]="showClearCartModal"
      title="Vaciar Carrito de Compras"
      message="¿Está seguro de que desea eliminar todos los productos del carrito actual?"
      confirmText="Sí, Vaciar"
      cancelText="Cancelar"
      [isDanger]="true"
      (confirmed)="executeClearCart()"
      (cancelled)="showClearCartModal = false">
    </app-confirm-modal>

    <!-- Completed Sale Receipt Modal -->
    <div *ngIf="lastCompletedSale" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs">
      <div class="bg-white rounded-2xl shadow-2xl border border-[#D8D3C1] max-w-md w-full overflow-hidden p-6 text-center">
        <div class="w-12 h-12 rounded-full bg-[#EBF5DE] text-[#3B6C2B] mx-auto flex items-center justify-center mb-3">
          <span class="material-symbols-outlined text-3xl">check</span>
        </div>
        <h3 class="text-xl font-bold font-headline text-[#1E2519]">¡Venta Registrada con Éxito!</h3>
        <p class="text-xs text-[#7D8774] mt-1 font-mono">Orden {{ lastCompletedSale.orderNumber }}</p>

        <!-- Receipt Summary -->
        <div class="my-4 p-3 bg-[#FAF8F0] rounded-xl border border-[#D8D3C1] text-left text-xs font-mono space-y-1.5">
          <div class="flex justify-between font-sans text-sm font-bold border-b border-[#D8D3C1] pb-1">
            <span>TOTAL PAGADO</span>
            <span class="text-[#3B6C2B]">Bs {{ lastCompletedSale.netTotal | number:'1.2-2' }}</span>
          </div>
          <div class="flex justify-between">
            <span>Método de Pago:</span>
            <span class="font-bold">{{ lastCompletedSale.paymentMethod }}</span>
          </div>
          <div class="flex justify-between">
            <span>Efectivo Recibido:</span>
            <span>Bs {{ lastCompletedSale.cashAmountReceived | number:'1.2-2' }}</span>
          </div>
          <div class="flex justify-between" *ngIf="lastCompletedSale.changeGiven > 0">
            <span class="font-bold text-[#3B6C2B]">Cambio Entregado:</span>
            <span class="font-bold text-[#3B6C2B]">Bs {{ lastCompletedSale.changeGiven | number:'1.2-2' }}</span>
          </div>
          <div class="flex justify-between text-[10px] text-[#7D8774] pt-1 border-t border-[#D8D3C1]">
            <span>13% IVA incluido:</span>
            <span>Bs {{ lastCompletedSale.tax13 | number:'1.2-2' }}</span>
          </div>
        </div>

        <div class="flex justify-center gap-3 mt-5">
          <button
            (click)="lastCompletedSale = null"
            class="px-5 py-2 rounded-xl text-sm font-semibold bg-[#3B6C2B] text-white hover:bg-[#4E7A3E] transition-colors">
            Nueva Venta
          </button>
        </div>
      </div>
    </div>
  `
})
export class PosTerminalComponent implements OnInit {
  allProducts: Product[] = [];
  categories: Category[] = [];
  selectedCategory = 'ALL';
  searchQuery = '';

  // Quick-Add Grouped Products (12 total in 3 groups)
  quickGroup1: Product[] = [];
  quickGroup2: Product[] = [];
  quickGroup3: Product[] = [];

  // Cart State
  cartItems: SaleItem[] = [];
  globalDiscount = 2.0; // Initial Bs 2.00 as in design
  customerName = 'Consumidor Final';

  // Payment State
  paymentMethod: PaymentMethod = 'CASH';
  cashReceived = 35.0; // Default example showing change
  qrReceived = 0.0;
  changeAmount = 7.45;

  // Modals
  showConfirmSaleModal = false;
  showClearCartModal = false;
  lastCompletedSale: Sale | null = null;

  constructor(
    private posService: PosService,
    private catalogService: CatalogService,
    private cashRegisterService: CashRegisterService,
    private sheetsSyncService: SheetsSyncService
  ) {}

  ngOnInit(): void {
    this.catalogService.products$.subscribe(prods => {
      this.allProducts = prods;
      this.initializeQuickAddGroups(prods);
    });

    this.catalogService.categories$.subscribe(cats => {
      this.categories = cats;
    });

    this.posService.cartItems$.subscribe(items => {
      this.cartItems = items;
      this.calculateChange();
    });

    this.posService.globalDiscount$.subscribe(disc => {
      this.globalDiscount = disc;
      this.calculateChange();
    });

    this.posService.customerName$.subscribe(name => {
      this.customerName = name;
    });
  }

  // Keyboard shortcut for F9 to trigger confirmation
  @HostListener('window:keydown', ['$event'])
  handleKeyboardEvent(event: KeyboardEvent): void {
    if (event.key === 'F9') {
      event.preventDefault();
      if (this.cartItems.length > 0) {
        this.triggerConfirmSaleModal();
      }
    }
  }

  private initializeQuickAddGroups(products: Product[]): void {
    // Group 1: Panadería & Desayuno (4 items)
    this.quickGroup1 = products.filter(p => ['PAN-001', 'PAN-002', 'PAN-003', 'LAC-001'].includes(p.code));
    // Group 2: Charcutería & Fiambres (4 items)
    this.quickGroup2 = products.filter(p => ['CHA-001', 'CHA-002', 'CHA-003', 'CHA-004'].includes(p.code));
    // Group 3: Bebidas & Snacks (4 items)
    this.quickGroup3 = products.filter(p => ['BEB-001', 'BEB-002', 'BEB-003', 'BEB-004'].includes(p.code));
  }

  get filteredProducts(): Product[] {
    let prods = this.allProducts.filter(p => p.isActive);

    if (this.selectedCategory !== 'ALL') {
      // Find category and filter by tag or group
      const cat = this.categories.find(c => c.id === this.selectedCategory);
      if (cat) {
        const catNameLower = cat.name.toLowerCase();
        prods = prods.filter(p => {
          if (catNameLower.includes('pan') && p.code.startsWith('PAN')) return true;
          if (catNameLower.includes('charcut') && p.code.startsWith('CHA')) return true;
          if (catNameLower.includes('bebida') && p.code.startsWith('BEB')) return true;
          if (catNameLower.includes('aceite') && p.code.startsWith('ABA')) return true;
          if (catNameLower.includes('snack') && p.code.startsWith('SNK')) return true;
          if (catNameLower.includes('lácteo') && p.code.startsWith('LAC')) return true;
          return false;
        });
      }
    }

    if (this.searchQuery.trim()) {
      const q = this.searchQuery.trim().toLowerCase();
      prods = prods.filter(p =>
        p.name.toLowerCase().includes(q) ||
        p.code.toLowerCase().includes(q) ||
        (p.barcode && p.barcode.toLowerCase().includes(q))
      );
    }

    return prods;
  }

  get totals() {
    return this.posService.calculateTotals();
  }

  handleSearchOrScan(): void {
    const q = this.searchQuery.trim();
    if (!q) return;

    // Direct barcode match
    const exactBarcodeMatch = this.allProducts.find(p => p.barcode === q && p.isActive);
    if (exactBarcodeMatch) {
      this.onSelectProduct(exactBarcodeMatch);
      this.searchQuery = '';
      return;
    }

    // Direct code match
    const exactCodeMatch = this.allProducts.find(p => p.code.toUpperCase() === q.toUpperCase() && p.isActive);
    if (exactCodeMatch) {
      this.onSelectProduct(exactCodeMatch);
      this.searchQuery = '';
      return;
    }

    // If single search result matches, add it
    if (this.filteredProducts.length === 1) {
      this.onSelectProduct(this.filteredProducts[0]);
      this.searchQuery = '';
    }
  }

  clearSearch(): void {
    this.searchQuery = '';
  }

  onSelectProduct(product: Product): void {
    try {
      this.posService.addProductToCart(product);
      this.calculateChange();
    } catch (e: any) {
      alert(e.message);
    }
  }

  changeQty(index: number, delta: number): void {
    const current = this.cartItems[index].quantity;
    this.posService.updateItemQuantity(index, current + delta);
    this.calculateChange();
  }

  onQtyInput(index: number, newQty: any): void {
    const val = Number(newQty);
    if (!isNaN(val) && val > 0) {
      this.posService.updateItemQuantity(index, val);
      this.calculateChange();
    }
  }

  onDiscountInput(index: number, newDiscount: any): void {
    const val = Number(newDiscount);
    if (!isNaN(val) && val >= 0) {
      try {
        this.posService.updateItemDiscount(index, val);
        this.calculateChange();
      } catch (err: any) {
        alert(err.message);
      }
    }
  }

  onGlobalDiscountChange(newVal: any): void {
    const val = Number(newVal);
    if (!isNaN(val) && val >= 0) {
      try {
        this.posService.setGlobalDiscount(val);
        this.calculateChange();
      } catch (err: any) {
        alert(err.message);
      }
    }
  }

  onCustomerNameChange(): void {
    this.posService.setCustomer(this.customerName);
  }

  removeItem(index: number): void {
    this.posService.removeItem(index);
    this.calculateChange();
  }

  confirmClearCart(): void {
    this.showClearCartModal = true;
  }

  executeClearCart(): void {
    this.posService.clearCart();
    this.showClearCartModal = false;
    this.calculateChange();
  }

  setExactCash(): void {
    this.cashReceived = this.totals.netTotal;
    this.calculateChange();
  }

  addCashBill(amount: number): void {
    this.cashReceived = Number(((this.cashReceived || 0) + amount).toFixed(2));
    this.calculateChange();
  }

  calculateChange(): void {
    const total = this.totals.netTotal;
    if (this.paymentMethod === 'CASH') {
      this.changeAmount = Number(Math.max(0, (this.cashReceived || 0) - total).toFixed(2));
    } else if (this.paymentMethod === 'QR') {
      this.cashReceived = 0;
      this.qrReceived = total;
      this.changeAmount = 0;
    } else if (this.paymentMethod === 'MIXED') {
      const combined = Number(((this.cashReceived || 0) + (this.qrReceived || 0)).toFixed(2));
      this.changeAmount = Number(Math.max(0, combined - total).toFixed(2));
    }
  }

  triggerConfirmSaleModal(): void {
    if (this.paymentMethod === 'CASH' && this.cashReceived < this.totals.netTotal) {
      alert(`El monto recibido (Bs ${this.cashReceived}) es menor al total a pagar (Bs ${this.totals.netTotal}).`);
      return;
    }
    this.showConfirmSaleModal = true;
  }

  executeSale(): void {
    try {
      const cash = this.paymentMethod === 'QR' ? 0 : (this.cashReceived || this.totals.netTotal);
      const qr = this.paymentMethod === 'QR' ? this.totals.netTotal : (this.paymentMethod === 'MIXED' ? (this.qrReceived || 0) : 0);

      const sale = this.posService.confirmSale(this.paymentMethod, cash, qr);
      this.showConfirmSaleModal = false;
      this.lastCompletedSale = sale;

      // Sync to Google Sheets if configured
      this.sheetsSyncService.syncSaleToSheets(sale).subscribe();

      // Reset cash helper
      this.cashReceived = 0;
    } catch (err: any) {
      alert('Error al confirmar venta: ' + err.message);
      this.showConfirmSaleModal = false;
    }
  }
}
