import { ComponentFixture, TestBed } from '@angular/core/testing';
import { VoidSaleModalComponent } from './void-sale-modal.component';
import { Sale } from '../../../../core/sale/models/sale.models';

describe('VoidSaleModalComponent', () => {
  let component: VoidSaleModalComponent;
  let fixture: ComponentFixture<VoidSaleModalComponent>;

  const mockSale: Sale = {
    id: 'sale-1',
    saleNumber: 'VTA-0001-001',
    cashSessionId: 'session-1',
    status: 'COMPLETADA',
    totalAmount: 50.0,
    paymentMethod: 'EFECTIVO',
    description: 'Venta té',
    createdBy: 'admin',
    createdAt: '2026-10-01T10:00:00Z'
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VoidSaleModalComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(VoidSaleModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('sale', mockSale);
    fixture.detectChanges();
  });

  it('debe crearse e iniciar con motivo vacío e inválido', () => {
    expect(component).toBeTruthy();
    expect(component.voidReason()).toBe('');
    expect(component.isValid).toBe(false);
  });

  it('RG-03: el motivo es obligatorio, sin largo mínimo', () => {
    component.voidReason.set('    ');
    expect(component.isValid).toBe(false);

    component.voidReason.set('mal');
    expect(component.isValid).toBe(true);

    component.voidReason.set('Error en digitación');
    expect(component.isValid).toBe(true);
  });

  it('debe emitir confirmed con el motivo cuando es válido', () => {
    let emittedReason = '';
    component.confirmed.subscribe((r) => (emittedReason = r.voidReason));

    component.voidReason.set('Error en digitación');
    component.handleConfirm();

    expect(emittedReason).toBe('Error en digitación');
  });

  it('no debe emitir confirmed si el motivo está en blanco', () => {
    let emitted = false;
    component.confirmed.subscribe(() => (emitted = true));

    component.voidReason.set('   ');
    component.handleConfirm();

    expect(emitted).toBe(false);
  });

  it('debe emitir close al invocar handleClose()', () => {
    let closed = false;
    component.close.subscribe(() => (closed = true));

    component.handleClose();
    expect(closed).toBe(true);
  });
});
