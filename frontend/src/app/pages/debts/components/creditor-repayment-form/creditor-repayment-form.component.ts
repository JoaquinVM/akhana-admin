import { Component, computed, effect, input, output, signal, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CashLimitWarningComponent } from '../../../../shared/components/cash-limit-warning/cash-limit-warning.component';
import { CashClosedNoteComponent } from '../../../../shared/components/cash-closed-note/cash-closed-note.component';
import {
  CreditorDebtType,
  CreditorHistoryEntry,
  CreditorRepaymentRequest,
  DebtAccount
} from '../../../../core/debt/models/debt.models';

// DEU-03 (062): the person's pending debts to tick; the total to pay is what the ticked ones still owe, each paid whole
@Component({
  selector: 'app-creditor-repayment-form',
  standalone: true,
  imports: [CommonModule, FormsModule, CashLimitWarningComponent, CashClosedNoteComponent],
  templateUrl: './creditor-repayment-form.component.html',
  styleUrls: ['./creditor-repayment-form.component.css']
})
export class CreditorRepaymentFormComponent {
  entries = input<CreditorHistoryEntry[]>([]);
  accounts = input<DebtAccount[]>([]);
  isSaving = input<boolean>(false);
  // The server's rejection, shown above the list
  errorMessage = input<string | null>(null);

  submitted = output<CreditorRepaymentRequest>();

  // Oldest first, as the history lists them
  pending = computed(() => this.entries().filter(e => e.type !== 'REPAYMENT' && !e.voided && (e.pendingAmount ?? 0) > 0));

  selected = signal<ReadonlySet<string>>(new Set());
  accountId = signal<string>('');
  comment = signal<string>('');
  // What the note learned from the server; only counts while Caja is chosen
  cashClosed = signal<boolean>(false);
  localError = signal<string | null>(null);

  chosen = computed(() => this.pending().filter(e => this.selected().has(this.key(e))));
  allSelected = computed(() => this.pending().length > 0 && this.chosen().length === this.pending().length);
  // In cents, so 0.1 + 0.2 never shows 0.30000000000000004
  total = computed(() => this.sumOf(this.chosen()));
  salesTotal = computed(() => this.sumOf(this.chosen().filter(e => e.type === 'SALE')));
  purchasesTotal = computed(() => this.sumOf(this.chosen().filter(e => e.type !== 'SALE')));
  repaysFromCash = computed(() => !!this.accounts().find(a => a.id === this.accountId())?.cashAccount);

  constructor() {
    // A new history (after paying or a refresh) starts with nothing ticked
    effect(() => {
      this.entries();
      untracked(() => {
        this.selected.set(new Set());
        this.accountId.set('');
        this.comment.set('');
        this.localError.set(null);
      });
    });
  }

  key(entry: CreditorHistoryEntry): string {
    return `${entry.type}:${entry.id}`;
  }

  label(entry: CreditorHistoryEntry): string {
    if (entry.type === 'SALE') {
      return `Venta ${entry.saleNumber}`;
    }
    return entry.type === 'SHIPPING' ? `Envío del pedido ${entry.purchaseNumber}` : `Pedido ${entry.purchaseNumber}`;
  }

  isSelected(entry: CreditorHistoryEntry): boolean {
    return this.selected().has(this.key(entry));
  }

  toggle(entry: CreditorHistoryEntry): void {
    const next = new Set(this.selected());
    const key = this.key(entry);
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    this.selected.set(next);
    this.localError.set(null);
  }

  toggleAll(): void {
    this.selected.set(this.allSelected() ? new Set() : new Set(this.pending().map(e => this.key(e))));
    this.localError.set(null);
  }

  submit(): void {
    if (this.chosen().length === 0) {
      this.localError.set('Marque las deudas que se pagan.');
      return;
    }
    if (!this.accountId()) {
      this.localError.set('Elija la cuenta desde la que se paga.');
      return;
    }
    this.localError.set(null);
    const comment = this.comment().trim();
    this.submitted.emit({
      accountId: this.accountId(),
      amount: this.total(),
      comment: comment.length > 0 ? comment : null,
      items: this.chosen().map(e => ({ type: e.type as CreditorDebtType, id: e.id }))
    });
  }

  private sumOf(entries: CreditorHistoryEntry[]): number {
    return entries.reduce((sum, e) => sum + Math.round((e.pendingAmount ?? 0) * 100), 0) / 100;
  }
}
