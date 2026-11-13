import { Component, inject, input, output, computed, signal, effect, untracked, WritableSignal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormArray, ReactiveFormsModule, Validators } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import {
  CloseCashRequest,
  CashCutItem,
  BOLIVIAN_DENOMINATIONS
} from '../../../../core/cash/models/cash.models';
import { CloseCashDraftService } from '../../../../core/cash/close-cash-draft.service';

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
  private readonly draftStore = inject(CloseCashDraftService);

  isOpen = input<boolean>(false);
  isLoading = input<boolean>(false);
  expectedCash = input<number>(0);
  // CAJ-15: the open cash session, whose count is kept in this browser
  sessionId = input<string | null>(null);

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

  // CAJ-04: bills and coins kept in reserve, counted as one amount each
  reserveBills = signal<number>(0);
  reserveCoins = signal<number>(0);

  // Bills and coins, shown in two columns
  billCuts = computed(() => this.cutsData().filter((r) => r.denomination >= 10));
  coinCuts = computed(() => this.cutsData().filter((r) => r.denomination < 10));

  // Each column adds its loose reserve to its cuts
  subtotalBills = computed(() => {
    return Math.round(
      (this.billCuts().reduce((acc, r) => acc + (r.cashQuantity + r.reserveQuantity) * r.denomination, 0)
        + this.reserveBills()) * 100
    ) / 100;
  });

  subtotalCoins = computed(() => {
    return Math.round(
      (this.coinCuts().reduce((acc, r) => acc + (r.cashQuantity + r.reserveQuantity) * r.denomination, 0)
        + this.reserveCoins()) * 100
    ) / 100;
  });

  form: FormGroup = this.fb.group({
    closingAmount: [0, [Validators.required, Validators.min(0)]],
    closingComment: ['', [Validators.maxLength(500)]],
    reserveBills: [0, [Validators.min(0)]],
    reserveCoins: [0, [Validators.min(0)]]
  });

  enteredClosingAmount = signal<number>(0);

  // While the modal loads what was written before, nothing is saved
  private restoring = false;

  // Total of the count: cuts and reserves
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
    this.form.get('reserveBills')?.valueChanges.subscribe((val) => this.onReserveChange(this.reserveBills, val));
    this.form.get('reserveCoins')?.valueChanges.subscribe((val) => this.onReserveChange(this.reserveCoins, val));
    // CAJ-15: every change of the form is kept, the comment and the direct amount too
    this.form.valueChanges.subscribe(() => this.saveDraft());

    effect(() => {
      if (this.isOpen()) {
        untracked(() => this.restoreState());
      }
    });
  }

  // CAJ-15: opens with what was written for this session, or empty
  private restoreState(): void {
    const id = this.sessionId();
    const draft = id ? this.draftStore.load(id) : null;
    this.restoring = true;
    this.cutsData.set(
      this.denominations.map((denom) => {
        const saved = draft?.cuts.find((c) => c.denomination === denom);
        return {
          denomination: denom,
          cashQuantity: saved?.cashQuantity ?? 0,
          reserveQuantity: saved?.reserveQuantity ?? 0
        };
      })
    );
    this.activeMode.set(draft?.mode ?? 'ARQUEO');
    this.form.reset({
      closingAmount: draft?.mode === 'DIRECTO' ? draft.closingAmount : 0,
      closingComment: draft?.closingComment ?? '',
      reserveBills: draft?.reserveBills ?? 0,
      reserveCoins: draft?.reserveCoins ?? 0
    });
    if (this.activeMode() === 'ARQUEO') {
      this.applyCutsToClosingAmount();
    }
    this.showCutsSection.set(true);
    this.restoring = false;
  }

  private onReserveChange(target: WritableSignal<number>, val: unknown): void {
    const n = Number(val);
    target.set(Number.isFinite(n) && n > 0 ? Math.round(n * 100) / 100 : 0);
    if (!this.restoring && this.activeMode() === 'ARQUEO') {
      this.applyCutsToClosingAmount();
    }
  }

  private saveDraft(): void {
    const id = this.sessionId();
    if (this.restoring || !id || !this.isOpen()) {
      return;
    }
    const value = this.form.getRawValue();
    this.draftStore.save({
      sessionId: id,
      mode: this.activeMode(),
      cuts: this.cutsData()
        .filter((r) => r.cashQuantity > 0 || r.reserveQuantity > 0)
        .map(({ denomination, cashQuantity, reserveQuantity }) => ({ denomination, cashQuantity, reserveQuantity })),
      reserveBills: this.reserveBills(),
      reserveCoins: this.reserveCoins(),
      closingAmount: Number(value.closingAmount) || 0,
      closingComment: value.closingComment ?? ''
    });
  }

  setMode(mode: 'ARQUEO' | 'DIRECTO'): void {
    this.activeMode.set(mode);
    if (mode === 'ARQUEO') {
      this.applyCutsToClosingAmount();
    }
    this.saveDraft();
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
    this.saveDraft();
  }

  clearAllCuts(): void {
    this.cutsData.set(
      this.denominations.map((denom) => ({
        denomination: denom,
        cashQuantity: 0,
        reserveQuantity: 0
      }))
    );
    this.form.patchValue({ reserveBills: 0, reserveCoins: 0 });
    if (this.activeMode() === 'ARQUEO') {
      this.applyCutsToClosingAmount();
    }
    this.saveDraft();
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

    // CAJ-04: the cuts and the reserves go only with the detailed count, whose total is the closing amount; a
    // direct amount never travels with a count that does not add up to it
    const detailed = this.activeMode() === 'ARQUEO';
    const activeCuts: CashCutItem[] = !detailed ? [] : this.cutsData()
      .filter((r) => r.cashQuantity > 0 || r.reserveQuantity > 0)
      .map((r) => ({
        denomination: r.denomination,
        cashQuantity: r.cashQuantity,
        reserveQuantity: r.reserveQuantity,
        subtotal: this.getRowSubtotal(r)
      }));

    const request: CloseCashRequest = {
      closingAmount,
      closingComment: val.closingComment ? val.closingComment.trim() : null,
      cuts: activeCuts.length > 0 ? activeCuts : undefined
    };
    if (detailed && this.reserveBills() > 0) {
      request.reserveBills = this.reserveBills();
    }
    if (detailed && this.reserveCoins() > 0) {
      request.reserveCoins = this.reserveCoins();
    }
    this.confirmed.emit(request);
  }

  onCancel(): void {
    this.close.emit();
  }
}
