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

  it('POS-12: calcula la utilidad que tendrá la línea con el costo del producto elegido', () => {
    const item: any = { id: 'i1', quantity: 2, subtotal: 30 };
    expect(component.assignmentProfit(item, { buyPrice: 12.5 } as any)).toBe(5);
    expect(component.assignmentProfit(item, { buyPrice: 16 } as any)).toBe(-2);
  });

  it('CAJ-11: el detalle muestra el día de operación de una venta cargada otro día', async () => {
    saleServiceMock.getSaleById.mockReturnValue(of({ ...mockCompletedSale, createdAt: '2026-11-01T04:20:00Z', operationDate: '2026-10-31' }));
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('saleId', 'sale-125');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Día de operación');
    expect(compiled.textContent).toContain('Día 31/10 · cargada el 01/11 00:20');
  });

  it('CAJ-11: sin marca cuando la venta es del día en que se cargó', async () => {
    saleServiceMock.getSaleById.mockReturnValue(of({ ...mockCompletedSale, operationDate: '2026-10-06' }));
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('saleId', 'sale-125');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Día de operación');
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

  it('DEU-05: desglosa lo pagado y la parte a crédito; toda a crédito muestra solo Crédito', () => {
    const partial: Sale = { ...mockCompletedSale, saleType: 'AL_COSTO', paymentMethod: 'QR', totalAmount: 120,
      amountQr: 40, amountCash: 0, creditAmount: 80, customerName: 'Tía Neida' };
    expect(component.getPaymentMethodsList(partial)).toEqual([
      { label: 'QR', amount: 40 },
      { label: 'Crédito', amount: 80 }
    ]);

    const full: Sale = { ...partial, paymentMethod: 'CREDITO', amountQr: 0, creditAmount: 120 };
    expect(component.getPaymentMethodsList(full)).toEqual([{ label: 'Crédito', amount: 120 }]);
  });

  it('P-39: muestra el comentario de la venta al costo', async () => {
    saleServiceMock.getSaleById.mockReturnValue(of({ ...mockCompletedSale, saleType: 'AL_COSTO', description: 'Mami precio costo' }));
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('saleId', 'sale-125');
    fixture.detectChanges();
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Concepto');
    expect(text).toContain('Mami precio costo');
  });

  it('CAT-11: la bolsita muestra sus kilos y «Otro peso» los kilos y el precio por kilo', async () => {
    const nut = { productId: 'prod-nut', productName: 'Nuez pelada', productCode: 'FSE-NUE', discountPerUnit: 0, saleUnit: 'KILOGRAM' as const };
    saleServiceMock.getSaleById.mockReturnValue(of({
      ...mockCompletedSale,
      items: [
        { ...nut, id: 'item-bag', unitPrice: 45, finalUnitPrice: 45, quantity: 2, subtotal: 90,
          presentationId: 'pres-1', presentationName: '1/2 libra', presentationWeightKg: 0.227, weightKg: 0.454 },
        { ...nut, id: 'item-weight', unitPrice: 210, finalUnitPrice: 210, quantity: 0.3, subtotal: 63 }
      ]
    }));
    fixture.componentRef.setInput('isOpen', true);
    fixture.componentRef.setInput('saleId', 'sale-125');
    fixture.detectChanges();
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('(0.454 kg)');
    expect(text).toContain('Bs 45.00');
    expect(text).not.toContain('Bs 45.00/kg');
    expect(text).toContain('0.300 kg');
    expect(text).toContain('Bs 210.00/kg');
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

  describe('POS-12: asignación desde el historial', () => {
    const pendingSale: Sale = {
      ...mockCompletedSale,
      items: [{
        id: 'item-free', productId: 'prod-sin', productName: 'Granola de coco', productCode: 'SIN-IDENT',
        unitPrice: 25, discountPerUnit: 0, finalUnitPrice: 25, quantity: 2, subtotal: 50,
        freeLineType: 'UNIDENTIFIED', freeName: 'Granola de coco', assignmentStatus: 'POR_ASIGNAR'
      }]
    };
    const assignedSale: Sale = {
      ...pendingSale,
      items: [{ ...pendingSale.items![0], productName: 'Granola coco 250 g', productCode: 'GRA-250',
        assignmentStatus: 'ASIGNADO', assignedBy: 'admin', assignedAt: '2026-10-27T12:00:00Z' }]
    };

    beforeEach(() => {
      saleServiceMock.getSaleById.mockReturnValue(of(pendingSale));
      saleServiceMock.assignItem = vi.fn().mockReturnValue(of(assignedSale));
    });

    it('solo el historial ofrece asignar la línea por asignar', async () => {
      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('saleId', 'sale-125');
      fixture.detectChanges();
      await fixture.whenStable();
      const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(text()).toContain('Por asignar');
      expect(text()).not.toContain('Asignar producto');

      fixture.componentRef.setInput('canAssign', true);
      fixture.detectChanges();
      expect(text()).toContain('Asignar producto');
      expect(text()).toContain('Sin identificar definitivo');
    });

    it('elegir un producto pide confirmar antes de asignar', async () => {
      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('saleId', 'sale-125');
      fixture.componentRef.setInput('canAssign', true);
      fixture.detectChanges();
      await fixture.whenStable();

      component.startAssignment(pendingSale.items![0]);
      component.chooseProduct({ id: 'prod-gra', code: 'GRA-250', name: 'Granola coco 250 g', buyPrice: 15.5 } as any);
      fixture.detectChanges();

      expect(saleServiceMock.assignItem).not.toHaveBeenCalled();
      const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(text).toContain('Asignar a GRA-250 · Granola coco 250 g, costo Bs 15.50. No se puede deshacer.');

      component.cancelAssignment();
      expect(component.chosenProduct()).toBeNull();
    });

    it('al asignar muestra la venta actualizada y la emite', async () => {
      const updated: Sale[] = [];
      component.saleUpdated.subscribe((sale) => updated.push(sale));
      fixture.componentRef.setInput('isOpen', true);
      fixture.componentRef.setInput('saleId', 'sale-125');
      fixture.componentRef.setInput('canAssign', true);
      fixture.detectChanges();
      await fixture.whenStable();

      component.assignTo(pendingSale, pendingSale.items![0], { id: 'prod-gra' } as any);
      fixture.detectChanges();

      expect(saleServiceMock.assignItem).toHaveBeenCalledWith('sale-125', 'item-free', 'prod-gra');
      expect(updated).toEqual([assignedSale]);
      const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(text).toContain('Granola coco 250 g');
      expect(text).toContain('Vendido como «Granola de coco»');
      expect(text).not.toContain('Asignar producto');
    });
  });
});
