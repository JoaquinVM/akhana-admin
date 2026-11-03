import { Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { CashService } from '../../../core/cash/cash.service';

/**
 * Note under an account list when Caja is chosen and the server says no session is open (CAJ-03).
 * The form disables its save button with closedChange. A failed query says nothing about the session: no note, no block.
 */
@Component({
  selector: 'app-cash-closed-note',
  standalone: true,
  templateUrl: './cash-closed-note.component.html',
  styleUrls: ['./cash-closed-note.component.css']
})
export class CashClosedNoteComponent implements OnInit {
  private readonly cashService = inject(CashService);
  private removed = false;

  // A form with several notes passes what the last answer said, so all of them agree
  readonly closedInForm = input<boolean | null>(null);
  readonly closedChange = output<boolean>();

  private readonly answered = signal<boolean>(false);
  readonly closed = computed(() => this.closedInForm() ?? this.answered());

  constructor() {
    inject(DestroyRef).onDestroy(() => (this.removed = true));
  }

  ngOnInit(): void {
    this.cashService.getCurrentSession().subscribe({
      next: (session) => this.setClosed(session === null || session === undefined),
      error: () => this.setClosed(false)
    });
  }

  // The query is not cancelled, so CashService finishes its state; a late answer no longer speaks for this list
  private setClosed(closed: boolean): void {
    if (this.removed) {
      return;
    }
    this.answered.set(closed);
    this.closedChange.emit(closed);
  }
}
