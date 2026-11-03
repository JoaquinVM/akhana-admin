import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Subject, of, throwError } from 'rxjs';
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
    expect(note().nativeElement.textContent).toContain('La caja está cerrada. Ábrala y vuelva a elegir Caja.');
  });

  it('dice al formulario si la caja está cerrada, y un error no cuenta como cerrada', () => {
    const emitted: boolean[] = [];
    fixture.componentInstance.closedChange.subscribe(closed => emitted.push(closed));
    mockCashService.getCurrentSession.mockReturnValue(of(null));
    fixture.detectChanges();
    expect(emitted).toEqual([true]);

    const failed = TestBed.createComponent(CashClosedNoteComponent);
    const failedEmitted: boolean[] = [];
    failed.componentInstance.closedChange.subscribe(closed => failedEmitted.push(closed));
    mockCashService.getCurrentSession.mockReturnValue(throwError(() => ({ status: 0 })));
    failed.detectChanges();
    expect(failedEmitted).toEqual([false]);
  });

  it('muestra lo que le pasa el formulario, aunque su propia consulta diga otra cosa', () => {
    mockCashService.getCurrentSession.mockReturnValue(of(null));
    fixture.componentRef.setInput('closedInForm', false);
    fixture.detectChanges();
    expect(note()).toBeNull();

    fixture.componentRef.setInput('closedInForm', true);
    fixture.detectChanges();
    expect(note()).not.toBeNull();
  });

  it('una respuesta que llega después de quitar la nota no se avisa al formulario', () => {
    const answer = new Subject<any>();
    const emitted: boolean[] = [];
    mockCashService.getCurrentSession.mockReturnValue(answer);
    fixture.componentInstance.closedChange.subscribe(closed => emitted.push(closed));
    fixture.detectChanges();

    fixture.destroy();
    answer.next(null);
    expect(emitted).toEqual([]);
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
