import { Component, OnInit, inject, output, signal } from '@angular/core';
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

  readonly closed = signal<boolean>(false);
  readonly closedChange = output<boolean>();

  ngOnInit(): void {
    this.cashService.getCurrentSession().subscribe({
      next: (session) => this.setClosed(session === null || session === undefined),
      error: () => this.setClosed(false)
    });
  }

  private setClosed(closed: boolean): void {
    this.closed.set(closed);
    this.closedChange.emit(closed);
  }
}
