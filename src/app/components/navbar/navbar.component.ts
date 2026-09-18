import { Component, EventEmitter, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { CashRegisterService } from '../../services/cash-register.service';
import { CashRegisterSession, User } from '../../models/domain.model';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <header class="bg-inverse-surface text-surface-container-lowest h-16 px-4 sm:px-6 flex items-center justify-between shadow-md select-none sticky top-0 z-40 border-b border-outline/20">
      
      <!-- Left: Logo & Register Indicator -->
      <div class="flex items-center gap-4 lg:gap-6">
        
        <!-- Mobile Menu Toggle Button -->
        <button
          type="button"
          (click)="mobileMenuOpen = !mobileMenuOpen"
          class="lg:hidden p-1.5 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors">
          <span class="material-symbols-outlined text-2xl">
            {{ mobileMenuOpen ? 'close' : 'menu' }}
          </span>
        </button>

        <!-- Brand Logo -->
        <a routerLink="/pos" class="flex items-center gap-2.5 cursor-pointer group">
          <div class="w-9 h-9 rounded-xl bg-primary-container flex items-center justify-center text-white shadow-xs group-hover:scale-105 transition-transform">
            <!-- Akhana 3-leaf motif icon -->
            <svg class="w-5 h-5 fill-current text-white" viewBox="0 0 24 24">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
            </svg>
          </div>
          <div class="flex flex-col">
            <div class="flex items-center gap-1.5">
              <span class="text-xl font-extrabold tracking-tight font-serif italic text-[#FAF7EC]">Akhana</span>
              <span class="text-[10px] uppercase tracking-wider font-extrabold bg-accent-gold text-amber-950 px-2 py-0.5 rounded-md border border-amber-300">
                Admin POS
              </span>
            </div>
            <span class="text-[9px] text-[#E2E8D8] tracking-widest -mt-0.5 hidden sm:block">SISTEMA INTEGRAL DE CAJA</span>
          </div>
        </a>

        <!-- Register Status Badge -->
        <div class="hidden md:flex items-center gap-2 pl-3 border-l border-outline/30">
          <ng-container *ngIf="currentRegister$ | async as reg; else noReg">
            <div
              (click)="onOpenRegisterModal()"
              class="flex items-center gap-2 bg-white/10 hover:bg-white/15 cursor-pointer px-3 py-1.5 rounded-xl border border-outline/20 transition-all">
              <span class="w-2.5 h-2.5 rounded-full bg-tertiary-fixed animate-pulse"></span>
              <span class="text-xs font-semibold text-white">{{ reg.registerNumber }}</span>
              <span class="text-[11px] text-outline-variant">• Base:</span>
              <span class="text-xs font-mono font-bold text-tertiary-fixed">Bs {{ reg.initialAmount | number:'1.2-2' }}</span>
            </div>
          </ng-container>
          <ng-template #noReg>
            <div
              (click)="onOpenRegisterModal()"
              class="flex items-center gap-2 bg-red-900/40 hover:bg-red-900/60 cursor-pointer px-3 py-1.5 rounded-xl border border-red-500/40 transition-all text-red-200">
              <span class="w-2.5 h-2.5 rounded-full bg-error"></span>
              <span class="text-xs font-semibold">Caja Cerrada (Abrir)</span>
            </div>
          </ng-template>
        </div>

      </div>

      <!-- Center Desktop Navigation: ALWAYS VISIBLE OPTIONS -->
      <nav class="hidden lg:flex items-center gap-1 xl:gap-2">
        <a
          routerLink="/pos"
          routerLinkActive="bg-white/20 text-white font-bold border-b-2 border-accent-gold"
          class="px-3.5 py-1.5 rounded-lg text-xs font-medium text-[#FAF8F0] hover:text-white hover:bg-white/10 transition-colors flex items-center gap-1.5">
          <span class="material-symbols-outlined text-base">point_of_sale</span>
          Terminal POS
        </a>
        <a
          routerLink="/products"
          routerLinkActive="bg-white/20 text-white font-bold border-b-2 border-accent-gold"
          class="px-3.5 py-1.5 rounded-lg text-xs font-medium text-[#FAF8F0] hover:text-white hover:bg-white/10 transition-colors flex items-center gap-1.5">
          <span class="material-symbols-outlined text-base">inventory_2</span>
          Productos
        </a>
        <a
          routerLink="/catalog"
          routerLinkActive="bg-white/20 text-white font-bold border-b-2 border-accent-gold"
          class="px-3.5 py-1.5 rounded-lg text-xs font-medium text-[#FAF8F0] hover:text-white hover:bg-white/10 transition-colors flex items-center gap-1.5">
          <span class="material-symbols-outlined text-base">category</span>
          Catálogo & Grupos
        </a>
        <a
          routerLink="/sales"
          routerLinkActive="bg-white/20 text-white font-bold border-b-2 border-accent-gold"
          class="px-3.5 py-1.5 rounded-lg text-xs font-medium text-[#FAF8F0] hover:text-white hover:bg-white/10 transition-colors flex items-center gap-1.5">
          <span class="material-symbols-outlined text-base">receipt_long</span>
          Ventas
        </a>
      </nav>

      <!-- Right: Cash Register Trigger & User Role Switcher -->
      <div class="flex items-center gap-2 sm:gap-3">
        
        <!-- Cash Register Trigger Button -->
        <button
          type="button"
          (click)="onOpenRegisterModal()"
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-white/10 hover:bg-white/20 text-white border border-outline/30 transition-all">
          <span class="material-symbols-outlined text-base text-accent-gold">payments</span>
          <span class="hidden sm:inline">Arqueo / Caja</span>
        </button>

        <!-- User Role Switcher -->
        <div class="flex items-center gap-2 bg-white/10 hover:bg-white/15 px-3 py-1.5 rounded-xl border border-outline/30">
          <div class="w-6 h-6 rounded-full bg-primary flex items-center justify-center text-white text-[11px] font-bold">
            {{ (currentUser$ | async)?.fullName?.charAt(0) || 'A' }}
          </div>
          <div class="flex flex-col text-left">
            <span class="text-xs font-bold text-white leading-tight">
              {{ (currentUser$ | async)?.fullName }}
            </span>
            <div class="flex items-center gap-1">
              <span
                [ngClass]="authService.isAdmin() ? 'bg-purple-800 text-purple-100' : 'bg-emerald-800 text-emerald-100'"
                class="text-[9px] font-extrabold uppercase px-1.5 rounded-xs">
                {{ (currentUser$ | async)?.role }}
              </span>
              <button
                type="button"
                (click)="toggleRole()"
                title="Alternar entre rol Administrador y Vendedor"
                class="text-[10px] text-accent-gold hover:underline cursor-pointer">
                [Cambiar]
              </button>
            </div>
          </div>
        </div>

      </div>

    </header>

    <!-- Mobile Drawer / Dropdown Menu -->
    <div
      *ngIf="mobileMenuOpen"
      class="lg:hidden bg-inverse-surface border-b border-outline/30 p-4 space-y-2 shadow-xl sticky top-16 z-30 animate-fadeIn">
      <a
        (click)="mobileMenuOpen = false"
        routerLink="/pos"
        routerLinkActive="bg-white/20 font-bold"
        class="block px-4 py-2.5 rounded-xl text-sm font-semibold text-white hover:bg-white/10 transition-colors flex items-center gap-2.5">
        <span class="material-symbols-outlined text-xl">point_of_sale</span>
        Terminal POS
      </a>
      <a
        (click)="mobileMenuOpen = false"
        routerLink="/products"
        routerLinkActive="bg-white/20 font-bold"
        class="block px-4 py-2.5 rounded-xl text-sm font-semibold text-white hover:bg-white/10 transition-colors flex items-center gap-2.5">
        <span class="material-symbols-outlined text-xl">inventory_2</span>
        Gestión de Productos
      </a>
      <a
        (click)="mobileMenuOpen = false"
        routerLink="/catalog"
        routerLinkActive="bg-white/20 font-bold"
        class="block px-4 py-2.5 rounded-xl text-sm font-semibold text-white hover:bg-white/10 transition-colors flex items-center gap-2.5">
        <span class="material-symbols-outlined text-xl">category</span>
        Catálogo (Categorías, Grupos, Proveedores, Etiquetas)
      </a>
      <a
        (click)="mobileMenuOpen = false"
        routerLink="/sales"
        routerLinkActive="bg-white/20 font-bold"
        class="block px-4 py-2.5 rounded-xl text-sm font-semibold text-white hover:bg-white/10 transition-colors flex items-center gap-2.5">
        <span class="material-symbols-outlined text-xl">receipt_long</span>
        Consulta de Ventas
      </a>
    </div>
  `
})
export class NavbarComponent {
  @Output() openRegisterModal = new EventEmitter<void>();

  currentUser$: Observable<User>;
  currentRegister$: Observable<CashRegisterSession | null>;
  mobileMenuOpen = false;

  constructor(
    public authService: AuthService,
    private cashRegisterService: CashRegisterService
  ) {
    this.currentUser$ = this.authService.currentUser$;
    this.currentRegister$ = this.cashRegisterService.currentRegister$;
  }

  toggleRole(): void {
    const nextRole = this.authService.isAdmin() ? 'VENDEDOR' : 'ADMIN';
    this.authService.switchRole(nextRole);
  }

  onOpenRegisterModal(): void {
    this.openRegisterModal.emit();
  }
}
