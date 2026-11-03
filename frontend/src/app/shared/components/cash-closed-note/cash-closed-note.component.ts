import { Component, OnInit, inject, signal } from '@angular/core';
import { CashService } from '../../../core/cash/cash.service';

/**
 * Note under an account list when Caja is chosen and the server says no session is open (CAJ-03).
 * It does not block: the server rejects on save. A failed query says nothing about the session, so it shows no note.
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

  ngOnInit(): void {
    this.cashService.getCurrentSession().subscribe({
      next: (session) => this.closed.set(session === null || session === undefined),
      error: () => this.closed.set(false)
    });
  }
}
