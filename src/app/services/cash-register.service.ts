import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CashRegisterSession, DenominationBreakdown } from '../models/domain.model';
import { StorageService } from './storage.service';

export const BOLIVIANO_DENOMINATIONS = [
  { value: 200, label: 'Bs 200 (Billete)', isBill: true },
  { value: 100, label: 'Bs 100 (Billete)', isBill: true },
  { value: 50, label: 'Bs 50 (Billete)', isBill: true },
  { value: 20, label: 'Bs 20 (Billete)', isBill: true },
  { value: 10, label: 'Bs 10 (Billete)', isBill: true },
  { value: 5, label: 'Bs 5 (Moneda/Billete)', isBill: false },
  { value: 2, label: 'Bs 2 (Moneda)', isBill: false },
  { value: 1, label: 'Bs 1 (Moneda)', isBill: false },
  { value: 0.5, label: '50 Centavos', isBill: false },
  { value: 0.2, label: '20 Centavos', isBill: false },
  { value: 0.1, label: '10 Centavos', isBill: false },
];

@Injectable({
  providedIn: 'root'
})
export class CashRegisterService {
  constructor(private storageService: StorageService) {}

  get currentRegister$(): Observable<CashRegisterSession | null> {
    return this.storageService.currentRegister$;
  }

  getCurrentRegister(): CashRegisterSession | null {
    return this.storageService.getCurrentRegister();
  }

  isRegisterOpen(): boolean {
    const reg = this.getCurrentRegister();
    return !!reg && reg.isOpen;
  }

  openRegister(initialAmount: number, cashierName: string, notes?: string): CashRegisterSession {
    const newSession: CashRegisterSession = {
      id: 'reg-' + Date.now(),
      registerNumber: 'Caja #01',
      openedAt: new Date().toISOString(),
      openedBy: cashierName,
      initialAmount: Math.max(0, initialAmount),
      isOpen: true,
      cashSalesTotal: 0,
      qrSalesTotal: 0,
      expectedCash: Math.max(0, initialAmount),
      notes: notes || 'Apertura de turno'
    };

    this.storageService.saveCurrentRegister(newSession);
    return newSession;
  }

  recordSaleTender(cashAmount: number, qrAmount: number): void {
    const current = this.getCurrentRegister();
    if (!current || !current.isOpen) {
      throw new Error('No hay una caja abierta para registrar transacciones');
    }

    const updated: CashRegisterSession = {
      ...current,
      cashSalesTotal: Number((current.cashSalesTotal + cashAmount).toFixed(2)),
      qrSalesTotal: Number((current.qrSalesTotal + qrAmount).toFixed(2)),
      expectedCash: Number((current.initialAmount + current.cashSalesTotal + cashAmount).toFixed(2))
    };

    this.storageService.saveCurrentRegister(updated);
  }

  calculateCountedCash(breakdown: DenominationBreakdown): number {
    let total = 0;
    for (const item of BOLIVIANO_DENOMINATIONS) {
      const count = breakdown[item.value.toString()] || 0;
      total += count * item.value;
    }
    return Number(total.toFixed(2));
  }

  closeRegister(
    cashierName: string,
    breakdown: DenominationBreakdown,
    notes?: string
  ): CashRegisterSession {
    const current = this.getCurrentRegister();
    if (!current || !current.isOpen) {
      throw new Error('No hay caja activa para cerrar.');
    }

    const countedCash = this.calculateCountedCash(breakdown);
    const difference = Number((countedCash - current.expectedCash).toFixed(2));

    const closedSession: CashRegisterSession = {
      ...current,
      isOpen: false,
      closedAt: new Date().toISOString(),
      closedBy: cashierName,
      countedCash,
      difference,
      breakdown,
      notes: notes || current.notes
    };

    // Save to history
    const history = [...this.storageService.getRegisterHistory()];
    history.unshift(closedSession);
    this.storageService.saveRegisterHistory(history);
    this.storageService.saveCurrentRegister(null);

    return closedSession;
  }
}
