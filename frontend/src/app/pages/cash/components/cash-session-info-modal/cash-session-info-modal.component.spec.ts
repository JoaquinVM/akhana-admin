import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CashSessionInfoModalComponent } from './cash-session-info-modal.component';
import { CashSessionSummary } from '../../../../core/cash/models/cash.models';
import { By } from '@angular/platform-browser';

describe('CashSessionInfoModalComponent', () => {
  let component: CashSessionInfoModalComponent;
  let fixture: ComponentFixture<CashSessionInfoModalComponent>;

  const mockSession: CashSessionSummary = {
    id: 'session-5',
    sessionNumber: 5,
    status: 'ABIERTA',
    openingAmount: 200.0,
    openingComment: 'Se inició la jornada con efectivo de reserva.',
    openedBy: 'Joaquín Viscafe',
    openedAt: '2026-10-02T09:15:00Z',
    closingAmount: null,
    closingComment: null,
    closedBy: null,
    closedAt: null,
    totalSalesCash: 0,
    totalSalesQr: 0,
    totalSales: 0,
    expectedCash: 200.0,
    difference: null,
    salesCount: 0
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CashSessionInfoModalComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(CashSessionInfoModalComponent);
    component = fixture.componentInstance;
  });

  it('debe crearse correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('debe mostrar número de sesión, quién abrió la caja, fecha/hora y comentario de apertura', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('session', mockSession);
    fixture.detectChanges();

    const textContent = fixture.nativeElement.textContent;
    expect(textContent).toContain('#5');
    expect(textContent).toContain('Joaquín Viscafe');
    expect(textContent).toContain('Se inició la jornada con efectivo de reserva.');
    expect(textContent).toContain('Hora de apertura');
  });

  it('no debe mostrar la sección de comentario si no existe comentario de apertura', () => {
    const sessionWithoutComment: CashSessionSummary = {
      ...mockSession,
      openingComment: null
    };

    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('session', sessionWithoutComment);
    fixture.detectChanges();

    const textContent = fixture.nativeElement.textContent;
    expect(textContent).toContain('#5');
    expect(textContent).toContain('Joaquín Viscafe');
    expect(textContent).not.toContain('Comentario de apertura');
    expect(fixture.debugElement.query(By.css('.info-comment'))).toBeNull();
  });

  it('no debe mostrar el estado de la caja dentro del modal', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('session', mockSession);
    fixture.detectChanges();

    const textContent = fixture.nativeElement.textContent;
    // No debe mostrar "ABIERTA", "Abierta" ni "Caja Abierta" en el contenido del modal
    expect(fixture.debugElement.query(By.css('.badge-active'))).toBeNull();
    expect(fixture.debugElement.query(By.css('.status-badge'))).toBeNull();
    expect(textContent).not.toContain('Caja Abierta');
    expect(textContent).not.toContain('ABIERTA');
  });

  it('no debe permitir cerrar la caja ni contener botones de cierre de caja', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('session', mockSession);
    fixture.detectChanges();

    const buttons = fixture.debugElement.queryAll(By.css('button'));
    for (const btn of buttons) {
      const text = (btn.nativeElement.textContent || '').trim().toLowerCase();
      expect(text).not.toBe('cerrar caja');
    }
  });

  it('debe emitir close al pulsar el botón Cerrar', () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('session', mockSession);
    fixture.detectChanges();

    let closeEmitted = false;
    component.close.subscribe(() => {
      closeEmitted = true;
    });

    const closeButton = fixture.debugElement.query(By.css('.modal-footer-actions button'));
    closeButton.nativeElement.click();

    expect(closeEmitted).toBe(true);
  });
});
