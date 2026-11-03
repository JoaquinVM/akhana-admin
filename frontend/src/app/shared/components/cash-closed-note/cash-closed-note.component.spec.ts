import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { CashClosedNoteComponent } from './cash-closed-note.component';
import { CashService } from '../../../core/cash/cash.service';

describe('CashClosedNoteComponent', () => {
  let fixture: ComponentFixture<CashClosedNoteComponent>;
  let mockCashService: any;

  beforeEach(async () => {
    mockCashService = { getCurrentSession: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [CashClosedNoteComponent],
      providers: [{ provide: CashService, useValue: mockCashService }]
    }).compileComponents();
    fixture = TestBed.createComponent(CashClosedNoteComponent);
  });

  const note = () => fixture.debugElement.query(By.css('.cash-closed-note'));

  it('avisa si el servidor responde que no hay caja abierta', () => {
    mockCashService.getCurrentSession.mockReturnValue(of(null));
    fixture.detectChanges();
    expect(note().nativeElement.textContent).toContain('La caja está cerrada. Para usar Caja, ábrala primero.');
  });

  it('no avisa con la caja abierta', () => {
    mockCashService.getCurrentSession.mockReturnValue(of({ id: 's1', expectedCash: 100 }));
    fixture.detectChanges();
    expect(note()).toBeNull();
  });

  it('no avisa si falla la consulta: un error no significa que esté cerrada', () => {
    mockCashService.getCurrentSession.mockReturnValue(throwError(() => ({ status: 0 })));
    fixture.detectChanges();
    expect(note()).toBeNull();
  });
});
