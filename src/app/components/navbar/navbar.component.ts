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
      
      <!-- Brand & Register Status -->
      <div class="flex items-center gap-6">
        
        <!-- Logo -->
        <a routerLink="/pos" class="flex items-center gap-3 cursor-pointer group">
          <div class="w-9 h-9 rounded-xl bg-primary-container flex items-center justify-center text-white shadow-xs group-hover:scale-105 transition-transform">
            <!-- Akhana 3-leaf motif icon -->
            <svg class="w-6 h-6 fill-current text-white" viewBox="0 0 24 24">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
            </svg>
          </div>
          <div class="flex flex-col">
            <div class="flex items-center gap-2">
              <span class="text-xl font-extrabold tracking-tight font-serif italic text-[#FAF7EC]">Akhana</span>
              <span class="text-[10px] uppercase tracking-wider font-extrabold bg-accent-gold text-amber-950 px-2 py-0.5 rounded-md border border-amber-300">
                Admin POS
              </span>
            </div>
            <span class="text-[9px] text-[#E2E8D8] tracking-widest -mt-0.5">SISTEMA INTEGRAL DE CAJA</span>
          </div>
        </a>

        <!-- Register Indicator -->
        <div class="hidden md:flex items-center gap-2 pl-4 border-l border-outline/30">
          <ng-container *ngIf="currentRegister$ | async as reg; else noReg">
            <div
              (click)="onOpenRegisterModal()"
              class="flex items-center gap-2 bg-white/10 hover:bg-white/15 cursor-pointer px-3 py-1.5 rounded-xl border border-outline/20 transition-all">
              <span class="w-2.5 h-2.5 rounded-full bg-tertiary-fixed animate-pulse"></span>
              <span class="text-xs font-semibold text-white">{{ reg.registerNumber }} (Abierta)</span>
              <span class="text-[11px] text-outline-variant">• Base:</span>
              <span class="text-xs font-mono font-bold text-tertiary-fixed">Bs {{ reg.initialAmount | number:'1.2-2' }}</span>
            </div>
          </ng-container>
          <ng-template #noReg>
            <div
              (click)="onOpenRegisterModal()"
              class="flex items-center gap-2 bg-red-900/40 hover:bg-red-900/60 cursor-pointer px-3 py-1.5 rounded-xl border border-red-500/40 transition-all text-red-200">
              <span class="w-2.5 h-2.5 rounded-full bg-error"></span>
              <span class="text-xs font-semibold">Caja Cerrada (Clic para Abrir)</span>
            </div>
          </ng-template>
        </div>

      </div>

      <!-- Navigation Links -->
      <nav class="hidden lg:flex items-center gap-1 xl:gap-2">
        <a
          routerLink="/pos"
          routerLinkActive="bg-white/15 text-white font-semibold border-b-2 border-primary-container"
          class="px-3.5 py-1.5 rounded-lg text-xs font-medium text-[#D8D3C1] hover:text-white hover:bg-white/10 transition-colors flex items-center gap-1.5">
          <span class="material-symbols-outlined text-base">point_of_sale</span>
          Terminal POS
        </a>
        <a
          routerLink="/sales"
          routerLinkActive="bg-white/15 text-white font-semibold border-b-2 border-primary-container"
          class="px-3.5 py-1.5 rounded-lg text-xs font-medium text-[#D8D3C1] hover:text-white hover:bg-white/10 transition-colors flex items-center gap-1.5">
          <span class="material-symbols-outlined text-base">receipt_long</span>
          Ventas
        </a>
        <a
          *ngIf="authService.isAdmin()"
          routerLink="/products"
          routerLinkActive="bg-white/15 text-white font-semibold border-b-2 border-primary-container"
          class="px-3.5 py-1.5 rounded-lg text-xs font-medium text-[#D8D3C1] hover:text-white hover:bg-white/10 transition-colors flex items-center gap-1.5">
          <span class="material-symbols-outlined text-base">inventory_2</span>
          Productos
        </a>
        <a
          *ngIf="authService.isAdmin()"
          routerLink="/catalog"
          routerLinkActive="bg-white/15 text-white font-semibold border-b-2 border-primary-container"
          class="px-3.5 py-1.5 rounded-lg text-xs font-medium text-[#D8D3C1] hover:text-white hover:bg-white/10 transition-colors flex items-center gap-1.5">
          <span class="material-symbols-outlined text-base">category</span>
          Catálogo
        </a>
      </nav>

      <!-- Right Actions: User Switcher & Cash Register Action -->
      <div class="flex items-center gap-3">
        
        <!-- Cash Register Trigger -->
        <button
          type="button"
          (click)="onOpenRegisterModal()"
          class="hidden sm:flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-white/10 hover:bg-white/20 text-white border border-outline/30 transition-all">
          <span class="material-symbols-outlined text-base text-accent-gold">payments</span>
          Arqueo / Caja
        </button>

        <!-- User Role Switcher -->
        <div class="flex items-center gap-2 bg-white/10 px-3 py-1.5 rounded-xl border border-outline/30">
          <div class="w-6 h-6 rounded-full bg-primary flex items-center justify-center text-white text-[11px] font-bold">
            {{ (currentUser$ | async)?.fullName?.charAt(0) || 'U' }}
          </div>
          <div class="flex flex-col text-left">
            <span class="text-xs font-bold text-white leading-tight">{{ (currentUser$ | async)?.fullName }}</span>
            <div class="flex items-center gap-1">
              <span
                [ngClass]="authService.isAdmin() ? 'bg-purple-900 text-purple-200' : 'bg-emerald-900 text-emerald-200'"
                class="text-[9px] font-extrabold uppercase px-1.5 rounded-sm">
                {{ (currentUser$ | async)?.role }}
              </span>
              <button
                (click)="toggleRole()"
                title="Alternar entre rol Administrador y Vendedor para probar permisos"
                class="text-[10px] text-accent-gold hover:underline cursor-pointer">
                [Cambiar]
              </button>
            </div>
          </div>
        </div>

      </div>

    </header>
  `
})
export class NavbarComponent {
  @Output() openRegisterModal = new EventEmitter<void>();

  currentUser$: Observable<User>;
  currentRegister$: Observable<CashRegisterSession | null>;

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
