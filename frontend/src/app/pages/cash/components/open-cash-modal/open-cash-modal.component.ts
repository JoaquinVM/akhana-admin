import { Component, computed, inject, input, output, effect, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { CashService } from '../../../../core/cash/cash.service';
import { CashOpeningProposal, OpenCashRequest } from '../../../../core/cash/models/cash.models';
import { shortDayLabel, weekdayDayLabel } from '../../../../shared/utils/store-date';

const REQUIRED_COMMENT = [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)];

@Component({
  selector: 'app-open-cash-modal',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ModalComponent],
  templateUrl: './open-cash-modal.component.html',
  styleUrls: ['./open-cash-modal.component.css']
})
export class OpenCashModalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly cashService = inject(CashService);

  isOpen = input<boolean>(false);
  isLoading = input<boolean>(false);
  // What the server answered: the banner of the POS stays behind this modal
  serverError = input<string | null>(null);

  close = output<void>();
  confirmed = output<OpenCashRequest>();

  // CAJ-10: previous session's closing, or the Caja balance for the first session; null while loading
  proposal = signal<CashOpeningProposal | null>(null);
  amountChanged = signal<boolean>(false);

  form: FormGroup = this.fb.group({
    operationDate: [''],
    openingAmount: [0, [Validators.required, Validators.min(0)]],
    openingComment: ['', [Validators.maxLength(500)]],
    noAttentionConfirmed: [false],
    noAttentionComment: ['', [Validators.maxLength(500)]]
  });

  private readonly openingAmount = toSignal(this.form.get('openingAmount')!.valueChanges, { initialValue: 0 });
  private readonly day = toSignal<string, string>(this.form.get('operationDate')!.valueChanges, { initialValue: '' });
  private readonly noAttentionConfirmed = toSignal<boolean, boolean>(this.form.get('noAttentionConfirmed')!.valueChanges, { initialValue: false });

  // CAJ-12: there is a day to choose unless the last session already is today's (or later)
  readonly hasDayToChoose = computed(() => {
    const proposal = this.proposal();
    return !proposal || !proposal.minDay || proposal.minDay <= proposal.today;
  });

  readonly noDayMessage = computed(() => {
    const proposal = this.proposal();
    if (!proposal || this.hasDayToChoose()) {
      return null;
    }
    return proposal.todayClosedSessionNumber !== null
      ? `La caja de hoy (#${proposal.todayClosedSessionNumber}) ya se cerró. Si fue por error, puedes reabrirla desde el Historial de cajas.`
      : `El día debe ser posterior al de la última caja (#${proposal.lastSessionNumber}, ${shortDayLabel(proposal.lastSessionDay!)}).`;
  });

  readonly dayInRange = computed(() => {
    const proposal = this.proposal();
    const day = this.day();
    return !proposal || (!!day && day <= proposal.today && (!proposal.minDay || day >= proposal.minDay));
  });

  readonly isPastDay = computed(() => {
    const proposal = this.proposal();
    const day = this.day();
    return !!proposal && !!day && day < proposal.today;
  });

  // CAJ-13: the rule of Sundays lives in the server; this only keeps the days before the chosen one
  readonly missingDays = computed(() => {
    const day = this.day();
    return day && this.dayInRange() ? (this.proposal()?.unresolvedDays ?? []).filter(missing => missing < day) : [];
  });

  readonly missingDayLabels = computed(() => this.missingDays().map(weekdayDayLabel));

  readonly commentRequired = computed(() => this.amountChanged() || this.isPastDay());

  readonly commentError = computed(() => {
    if (this.isPastDay()) {
      return this.amountChanged()
        ? 'Elegiste un día anterior y cambiaste el monto: indica el motivo (basta un comentario).'
        : 'Elegir un día anterior pide un comentario.';
    }
    return 'El monto es distinto al cierre anterior: indica el motivo.';
  });

  // Until the missing days are confirmed the session does not open; another day can be chosen instead
  readonly daysResolved = computed(() => this.missingDays().length === 0 || this.noAttentionConfirmed());

  constructor() {
    effect(() => {
      if (this.isOpen()) {
        this.proposal.set(null);
        this.form.reset({ operationDate: '', openingAmount: 0, openingComment: '', noAttentionConfirmed: false, noAttentionComment: '' });
        this.cashService.getOpeningProposal().subscribe({
          next: (proposal) => {
            this.proposal.set(proposal);
            this.form.patchValue({ operationDate: proposal.today, openingAmount: proposal.proposedAmount });
          }
        });
      }
    });

    // The comment becomes required if the amount differs from the proposal or the day is a previous one
    // (the backend validates it too); one comment is enough for both
    effect(() => {
      const amount = this.openingAmount();
      const proposal = this.proposal();
      const changed = proposal !== null && amount !== null && amount !== ''
        && Math.round(Number(amount) * 100) !== Math.round(proposal.proposedAmount * 100);
      this.amountChanged.set(changed);
      const comment = this.form.get('openingComment')!;
      comment.setValidators(this.commentRequired() ? REQUIRED_COMMENT : [Validators.maxLength(500)]);
      comment.updateValueAndValidity({ emitEvent: false });
    });

    // CAJ-13: the missing days have a comment of their own, which also goes with the marks
    effect(() => {
      const comment = this.form.get('noAttentionComment')!;
      comment.setValidators(this.missingDays().length > 0 ? REQUIRED_COMMENT : [Validators.maxLength(500)]);
      comment.updateValueAndValidity({ emitEvent: false });
    });

    // A confirmation only counts for the days it was given for
    effect(() => {
      this.day();
      if (this.form.get('noAttentionConfirmed')!.value) {
        this.form.patchValue({ noAttentionConfirmed: false });
      }
    });
  }

  canSubmit(): boolean {
    return this.form.valid && !this.isLoading() && this.hasDayToChoose() && this.dayInRange() && this.daysResolved();
  }

  onSubmit(): void {
    if (!this.canSubmit()) {
      this.form.markAllAsTouched();
      return;
    }

    const val = this.form.value;
    const missing = this.missingDays();
    this.confirmed.emit({
      openingAmount: Number(val.openingAmount),
      openingComment: val.openingComment ? val.openingComment.trim() : null,
      operationDate: val.operationDate || null,
      noAttentionDays: missing.length > 0 ? missing : null,
      noAttentionComment: missing.length > 0 ? val.noAttentionComment.trim() : null
    });
  }

  onCancel(): void {
    this.close.emit();
  }
}
