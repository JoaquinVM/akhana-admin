import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './components/navbar/navbar.component';
import { RegisterModalComponent } from './components/register-modal/register-modal.component';
import { CashRegisterService } from './services/cash-register.service';
import { AuthService } from './services/auth.service';
import { SheetsSyncService } from './services/sheets-sync.service';
import { CashRegisterSession, DenominationBreakdown } from './models/domain.model';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent, RegisterModalComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  isRegisterModalOpen = false;
  registerModalMode: 'OPEN' | 'CLOSE' = 'OPEN';
  currentRegister: CashRegisterSession | null = null;

  constructor(
    private cashRegisterService: CashRegisterService,
    private authService: AuthService,
    private sheetsSyncService: SheetsSyncService
  ) {}

  ngOnInit(): void {
    this.cashRegisterService.currentRegister$.subscribe(reg => {
      this.currentRegister = reg;
    });
  }

  handleOpenRegisterModal(): void {
    if (this.currentRegister && this.currentRegister.isOpen) {
      this.registerModalMode = 'CLOSE';
    } else {
      this.registerModalMode = 'OPEN';
    }
    this.isRegisterModalOpen = true;
  }

  onConfirmOpenRegister(data: { initialAmount: number; notes?: string }): void {
    const user = this.authService.currentUser;
    this.cashRegisterService.openRegister(data.initialAmount, user.fullName, data.notes);
    this.isRegisterModalOpen = false;
  }

  onConfirmCloseRegister(data: { breakdown: DenominationBreakdown; notes?: string }): void {
    const user = this.authService.currentUser;
    const closed = this.cashRegisterService.closeRegister(user.fullName, data.breakdown, data.notes);
    this.isRegisterModalOpen = false;

    // Sync to Google Sheets if configured
    this.sheetsSyncService.syncRegisterClose(closed).subscribe();

    alert(`Arqueo de caja completado. Efectivo contado: Bs ${closed.countedCash?.toFixed(2)} | Diferencia: Bs ${closed.difference?.toFixed(2)}`);
  }
}
