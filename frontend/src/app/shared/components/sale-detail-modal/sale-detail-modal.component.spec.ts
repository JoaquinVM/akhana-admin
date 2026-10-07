import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SaleDetailModalComponent } from './sale-detail-modal.component';
import { SaleService } from '../../../core/sale/sale.service';
import { Sale } from '../../../core/cash/models/cash.models';
import { of, throwError } from 'rxjs';
import { By } from '@angular/platform-browser';

describe('SaleDetailModalComponent', () => {
  let component: SaleDetailModalComponent;
  let fixture: ComponentFixture<SaleDetailModalComponent>;
  let saleServiceMock: any;

  const mockCompletedSale: Sale = {
    id: 'sale-125',
    saleNumber: '125',
    cashSessionId: 'session-5',
    sessionNumber: 5,
    status: 'COMPLETADA',
    subtotalAmount: 100.0,
    discountItemsTotal: 8.0,
    globalDiscountAmount: 5.0,
    discountTotal: 13.0,
    totalAmount: 87.0,
    paymentMethod: 'MIXTO',
    amountCash: 30.0,
    amountQr: 25.0,
    amountReceived: 50.0,
    changeGiven: 20.0,
    createdBy: 'Juan Pérez',
    createdAt: '2026-10-06T15:32:45Z',
    items: [
      {
        id: 'item-1',
        productId: 'prod-1',
        productName: 'Té Verde Orgánico',
        productCode: 'TE-001',
        unitPrice: 20.0,
        discountPerUnit: 2.0,
        finalUnitPrice: 18.0,
        quantity: 4,
        subtotal: 72.0
      },
      {
        id: 'item-2',
        productId: 'prod-2',
        productName: 'Miel de Abeja',
        productCode: 'MEL-002',
        unitPrice: 20.0,
        discountPerUnit: 0.0,
        finalUnitPrice: 20.0,
        quantity: 1,
        subtotal: 20.0
      }
    ]
  };

  const mockVoidedSale: Sale = {
    ...mockCompletedSale,
    id: 'sale-126',
    saleNumber: '126',
    status: 'ANULADA',
    voidedAt: '2026-10-06T16:10:32Z',
    voidedBy: 'Juan Pérez',
    voidReason: 'Error en la venta'
  };

  beforeEach(async () => {
    saleServiceMock = {
      getSaleById: vi.fn().mockReturnValue(of(mockCompletedSale))
    };

    await TestBed.configureTestingModule({
      imports: [SaleDetailModalComponent],
      providers: [
        { provide: SaleService, useValue: saleServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SaleDetailModalComponent);
    component = fixture.componentInstance;
  });

  it('debe crearse correctamente', () => {
    expect(component).toBeTruthy();
  });

  it('debe cargar la venta por ID y mostrar información general al abrirse', async () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('saleId', 'sale-125');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(saleServiceMock.getSaleById).toHaveBeenCalledWith('sale-125');
    expect(component.currentSale()).toEqual(mockCompletedSale);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Venta #125');
    expect(compiled.textContent).toContain('Juan Pérez');
    expect(compiled.textContent).toContain('Sesión #5');
    expect(compiled.textContent).toContain('Completada');
  });

  it('debe desglosar los productos, precios, descuentos y subtotales en la tabla', async () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('saleId', 'sale-125');
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Té Verde Orgánico');
    expect(compiled.textContent).toContain('TE-001');
    expect(compiled.textContent).toContain('Bs 20.00');
    // Descuento con monto y porcentaje: 2 * 4 = 8.00 (10%)
    expect(compiled.textContent).toContain('Bs 8.00');
    expect(compiled.textContent).toContain('(10%)');
    // Producto sin descuento
    expect(compiled.textContent).toContain('Miel de Abeja');
    expect(compiled.textContent).toContain('Bs 0,00');
  });

  it('debe mostrar el resumen de descuentos acumulados y total neto', async () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('saleId', 'sale-125');
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Monto sin descuento');
    expect(compiled.textContent).toContain('Bs 100.00');
    expect(compiled.textContent).toContain('Descuentos por productos');
    expect(compiled.textContent).toContain('-Bs 8.00');
    expect(compiled.textContent).toContain('Descuento por venta');
    expect(compiled.textContent).toContain('-Bs 5.00');
    expect(compiled.textContent).toContain('Total descuentos acumulados');
    expect(compiled.textContent).toContain('-Bs 13.00');
    expect(compiled.textContent).toContain('Bs 87.00');
  });

  it('debe mostrar los métodos de pago con sus respectivos montos', async () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('saleId', 'sale-125');
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Efectivo');
    expect(compiled.textContent).toContain('Bs 30.00');
    expect(compiled.textContent).toContain('QR');
    expect(compiled.textContent).toContain('Bs 25.00');
    expect(compiled.textContent).toContain('Monto recibido:');
    expect(compiled.textContent).toContain('Bs 50.00');
    expect(compiled.textContent).toContain('Cambio:');
    expect(compiled.textContent).toContain('Bs 20.00');
  });

  it('debe mostrar la sección de información de anulación para ventas con estado ANULADA', async () => {
    saleServiceMock.getSaleById.mockReturnValue(of(mockVoidedSale));
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('saleId', 'sale-126');
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Anulada');
    expect(compiled.textContent).toContain('Información de anulación');
    expect(compiled.textContent).toContain('Error en la venta');
    expect(compiled.textContent).toContain('Juan Pérez');

    // Mantiene visibles los productos originales
    expect(compiled.textContent).toContain('Té Verde Orgánico');
    expect(compiled.textContent).toContain('Bs 87.00');
  });

  it('debe liberar la memoria de la venta al cerrarse el modal', async () => {
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('saleId', 'sale-125');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.currentSale()).not.toBeNull();

    // Se cierra el modal
    fixture.componentRef.setInput('isOpen', false);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.currentSale()).toBeNull();
  });

  it('debe emitir close cuando se pulsa el botón Cerrar', async () => {
    const closeSpy = vi.fn();
    component.close.subscribe(closeSpy);

    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('saleId', 'sale-125');
    fixture.detectChanges();
    await fixture.whenStable();

    const closeBtn = fixture.debugElement.query(By.css('.modal-footer-actions .btn-secondary'));
    expect(closeBtn).toBeTruthy();
    closeBtn.nativeElement.click();

    expect(closeSpy).toHaveBeenCalled();
  });

  it('debe mostrar mensaje de error si falla la consulta y no hay fallback', async () => {
    saleServiceMock.getSaleById.mockReturnValue(throwError(() => new Error('Error de conexión')));
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('saleId', 'sale-error');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.errorMessage()).toContain('No se pudo cargar la información de la venta');
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('No se pudo cargar la información de la venta');
  });
});
