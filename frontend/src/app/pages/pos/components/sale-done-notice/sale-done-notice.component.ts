import { Component, input, output } from '@angular/core';

// H3: the sale just registered, with a way to open it, while the next sale starts in the same screen
@Component({
  selector: 'app-sale-done-notice',
  standalone: true,
  template: `
    <div class="sale-done" role="status">
      <span class="sale-done-text">Venta {{ saleNumber() }} registrada</span>
      <span class="sale-done-sep" aria-hidden="true">·</span>
      <button type="button" class="sale-done-link" (click)="view.emit()">Ver</button>
      <button type="button" class="sale-done-close" (click)="dismiss.emit()" aria-label="Cerrar aviso">×</button>
    </div>
  `,
  styles: `
    .sale-done {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.625rem 0.875rem;
      border-radius: var(--radius-md);
      background-color: #ecfdf5;
      border: 1px solid #a7f3d0;
      color: #065f46;
      font-size: 0.875rem;
    }
    .sale-done-text { font-weight: 600; }
    .sale-done-link {
      background: none; border: none; padding: 0; cursor: pointer;
      color: #065f46; font: inherit; font-weight: 700; text-decoration: underline;
    }
    .sale-done-close {
      margin-left: auto; background: none; border: none; cursor: pointer;
      color: inherit; font-size: 1.1rem; line-height: 1; padding: 0 0.25rem;
    }
  `
})
export class SaleDoneNoticeComponent {
  readonly saleNumber = input.required<string>();
  readonly view = output<void>();
  readonly dismiss = output<void>();
}
