import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReadingNoticeComponent } from './reading-notice.component';
import { PurchaseDraft } from '../../../../core/purchase/models/purchase.models';

describe('ReadingNoticeComponent', () => {
  let fixture: ComponentFixture<ReadingNoticeComponent>;
  const draft = (overrides: Partial<PurchaseDraft>) =>
    ({ readingId: 'r', source: 'PHOTO', supplierId: 's', supplierText: null, reviewAll: false, paidNote: false,
       lines: [], shippingCost: null, readTotal: 100, pendingPurchases: [], ...overrides }) as PurchaseDraft;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ReadingNoticeComponent] }).compileComponents();
    fixture = TestBed.createComponent(ReadingNoticeComponent);
  });

  function render(values: Record<string, unknown>): string {
    Object.entries(values).forEach(([name, value]) => fixture.componentRef.setInput(name, value));
    fixture.detectChanges();
    return fixture.nativeElement.textContent;
  }

  it('CA-18: avisa la diferencia con el total leído', () => {
    const text = render({ draft: draft({}), totalAmount: 90.5, linesComplete: true });
    expect(text).toContain('diferencia de Bs 9.50');
  });

  it('CA-18: sin diferencia, sin aviso, también con decimales de punto flotante', () => {
    expect(render({ draft: draft({ readTotal: 0.3 }), totalAmount: 0.1 + 0.2, linesComplete: true })).not.toContain('diferencia');
  });

  it('CA-18: no compara mientras falten líneas o costos', () => {
    expect(render({ draft: draft({}), totalAmount: 90, linesComplete: false })).not.toContain('diferencia');
  });

  it('una nota suelta pide revisar todo', () => {
    expect(render({ draft: draft({ reviewAll: true }) })).toContain('revise todas las líneas');
  });

  it('CA-07: un proveedor que no coincide pide elegirlo', () => {
    expect(render({ draft: draft({ supplierId: null, supplierText: 'Nadie' }) })).toContain('«Nadie»');
  });

  it('CA-19: propone el pago con origen vacío, y se puede quitar', () => {
    const dismissed = vi.fn();
    fixture.componentInstance.dismissPaid.subscribe(dismissed);
    expect(render({ draft: draft({ paidNote: true }), paidProposal: true })).toContain('elija la caja, la cuenta o la persona');
    (fixture.nativeElement.querySelector('.paid-proposal button') as HTMLButtonElement).click();
    expect(dismissed).toHaveBeenCalled();
  });
});
