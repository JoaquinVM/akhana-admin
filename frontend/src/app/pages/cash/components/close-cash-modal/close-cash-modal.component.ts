import { Component, inject, input, output, computed, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormArray, ReactiveFormsModule, Validators } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import {
  CloseCashRequest,
  CashCutItem,
  BOLIVIAN_DENOMINATIONS
} from '../../../../core/cash/models/cash.models';

interface CutRow {
  denomination: number;
  cashQuantity: number;
  reserveQuantity: number;
}

@Component({
  selector: 'app-close-cash-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ModalComponent],
  templateUrl: './close-cash-modal.component.html',
  styleUrls: ['./close-cash-modal.component.css']
})
export class CloseCashModalComponent {
  private readonly fb = inject(FormBuilder);

  isOpen = input<boolean>(false);
  isLoading = input<boolean>(false);
  expectedCash = input<number>(0);

  close = output<void>();
  confirmed = output<CloseCashRequest>();

  denominations = BOLIVIAN_DENOMINATIONS;
  showCutsSection = signal<boolean>(false);

  activeMode = signal<'ARQUEO' | 'DIRECTO'>('ARQUEO');

  // Count per bill and coin
  cutsData = signal<CutRow[]>(
    this.denominations.map((denom) => ({
      denomination: denom,
      cashQuantity: 0,
      reserveQuantity: 0
    }))
  );

  // Bills and coins, shown in two columns
  billCuts = computed(() => this.cutsData().filter((r) => r.denomination >= 10));
  coinCuts = computed(() => this.cutsData().filter((r) => r.denomination < 10));

  subtotalBills = computed(() => {
    return Math.round(
      this.billCuts().reduce((acc, r) => acc + (r.cashQuantity + r.reserveQuantity) * r.denomination, 0) * 100
    ) / 100;
  });

  subtotalCoins = computed(() => {
    return Math.round(
      this.coinCuts().reduce((acc, r) => acc + (r.cashQuantity + r.reserveQuantity) * r.denomination, 0) * 100
    ) / 100;
  });

  form: FormGroup = this.fb.group({
    closingAmount: [0, [Validators.required, Validators.min(0)]],
    closingComment: ['', [Validators.maxLength(500)]]
  });

  enteredClosingAmount = signal<number>(0);

  // Total of the count
  totalCuts = computed(() => {
    return Math.round((this.subtotalBills() + this.subtotalCoins()) * 100) / 100;
  });

  // Rounded to cents so an exact count reads as 0, never as a float remainder
  difference = computed(() => {
    return Math.round((this.enteredClosingAmount() - this.expectedCash()) * 100) / 100;
  });

  constructor() {
    this.form.get('closingAmount')?.valueChanges.subscribe((val) => {
      this.enteredClosingAmount.set(Number(val) || 0);
    });

    effect(() => {
      if (this.isOpen()) {
        this.resetState();
      }
    });
  }

  private resetState(): void {
    this.form.reset({
      closingAmount: 0,
      closingComment: ''
    });
    this.cutsData.set(
      this.denominations.map((denom) => ({
        denomination: denom,
        cashQuantity: 0,
        reserveQuantity: 0
      }))
    );
    this.activeMode.set('ARQUEO');
    this.showCutsSection.set(true);
    this.enteredClosingAmount.set(0);
  }

  setMode(mode: 'ARQUEO' | 'DIRECTO'): void {
    this.activeMode.set(mode);
    if (mode === 'ARQUEO') {
      this.applyCutsToClosingAmount();
    }
  }

  toggleCutsSection(): void {
    this.showCutsSection.update((val) => !val);
  }

  updateCutQuantity(denomination: number, field: 'cashQuantity' | 'reserveQuantity', event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = Math.max(0, parseInt(input.value, 10) || 0);

    this.cutsData.update((rows) =>
      rows.map((r) =>
        r.denomination === denomination
          ? { ...r, [field]: value }
          : r
      )
    );

    // In detailed mode the count is the closing amount
    if (this.activeMode() === 'ARQUEO') {
      this.applyCutsToClosingAmount();
    }
  }

  clearAllCuts(): void {
    this.cutsData.set(
      this.denominations.map((denom) => ({
        denomination: denom,
        cashQuantity: 0,
        reserveQuantity: 0
      }))
    );
    if (this.activeMode() === 'ARQUEO') {
      this.applyCutsToClosingAmount();
    }
  }

  applyCutsToClosingAmount(): void {
    const total = this.totalCuts();
    this.form.patchValue({ closingAmount: total });
    this.form.get('closingAmount')?.markAsTouched();
  }

  getRowSubtotal(row: CutRow): number {
    return Math.round((row.cashQuantity + row.reserveQuantity) * row.denomination * 100) / 100;
  }

  onSubmit(): void {
    if (this.form.invalid || this.isLoading()) {
      this.form.markAllAsTouched();
      return;
    }

    const val = this.form.value;
    const closingAmount = Number(val.closingAmount);

    // CAJ-04: the cuts go only with the detailed count, whose total is the closing amount; a direct amount
    // never travels with a count that does not add up to it
    const activeCuts: CashCutItem[] = this.activeMode() !== 'ARQUEO' ? [] : this.cutsData()
      .filter((r) => r.cashQuantity > 0 || r.reserveQuantity > 0)
      .map((r) => ({
        denomination: r.denomination,
        cashQuantity: r.cashQuantity,
        reserveQuantity: r.reserveQuantity,
        subtotal: this.getRowSubtotal(r)
      }));

    this.confirmed.emit({
      closingAmount,
      closingComment: val.closingComment ? val.closingComment.trim() : null,
      cuts: activeCuts.length > 0 ? activeCuts : undefined
    });
  }

  onCancel(): void {
    this.close.emit();
  }
}
