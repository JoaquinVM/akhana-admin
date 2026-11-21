import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { BoxProposal } from '../../../core/product/models/product.models';
import { ProductService } from '../../../core/product/product.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { BoxConversionComponent } from './box-conversion.component';

// COM-10: the boxes sold as their own product become packages of their base product, one by one
describe('BoxConversionComponent', () => {
  let fixture: ComponentFixture<BoxConversionComponent>;
  let component: BoxConversionComponent;
  let products: {
    getBoxProposals: ReturnType<typeof vi.fn>;
    convertBox: ReturnType<typeof vi.fn>;
    discardBox: ReturnType<typeof vi.fn>;
  };

  const chia: BoxProposal = {
    boxProductId: 'box-chia', boxCode: 'NSE-BAR-CHI-CAJ', boxName: 'Barra Chia Caja x12', boxSellPrice: 44, boxBuyPrice: 25.5,
    boxBarcodes: ['7790000000017'], supplierName: 'Natuser', baseProductId: 'chia', baseCode: 'NSE-BAR-CHI',
    baseName: 'Barra Chia', baseSellPrice: 4, baseBuyPrice: 2.13, proposedName: 'Caja ×12', proposedBaseQuantity: 12,
    proposedPrice: 44
  };
  const roquin: BoxProposal = {
    ...chia, boxProductId: 'box-roq', boxCode: 'NSE-BAR-ROQ-CAJ', boxName: 'Roquin Caja x24', boxBarcodes: [],
    baseProductId: 'roq', baseName: 'Roquin', proposedName: 'Caja ×24', proposedBaseQuantity: 24, proposedPrice: 88
  };

  beforeEach(async () => {
    products = {
      getBoxProposals: vi.fn().mockReturnValue(of([chia, roquin])),
      convertBox: vi.fn().mockReturnValue(of({ id: 'chia' })),
      discardBox: vi.fn().mockReturnValue(of(undefined))
    };
    await TestBed.configureTestingModule({
      imports: [BoxConversionComponent],
      providers: [
        provideRouter([]),
        { provide: ProductService, useValue: products },
        { provide: ToastService, useValue: { success: vi.fn(), error: vi.fn() } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(BoxConversionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  const text = (): string => fixture.nativeElement.textContent;

  it('muestra cada caja con su producto base y lo propuesto, y cuántas faltan', () => {
    expect(text()).toContain('Faltan 2');
    expect(text()).toContain('Barra Chia Caja x12');
    expect(text()).toContain('empaque de Barra Chia');
    expect(component.draft(chia)).toEqual({ name: 'Caja ×12', baseQuantity: 12, fixedPrice: 44, barcode: '7790000000017' });
    // 12 x 2,13: the cost the package will have
    expect(component.packageCost(chia)).toBe(25.56);
  });

  it('confirmar manda lo corregido y saca la caja de la lista', () => {
    component.update(chia, 'name', ' Caja x 12 ');
    component.update(chia, 'fixedPrice', '');
    component.confirm(chia);
    fixture.detectChanges();

    expect(products.convertBox).toHaveBeenCalledWith('box-chia',
      { name: 'Caja x 12', baseQuantity: 12, fixedPrice: null, barcode: '7790000000017' });
    expect(component.proposals().map(p => p.boxProductId)).toEqual(['box-roq']);
    expect(text()).toContain('Faltan 1');
  });

  it('si el servidor rechaza el empaque, el mensaje queda en esa caja y la caja sigue', () => {
    products.convertBox.mockReturnValue(throwError(() => ({ error: { message: 'Ya existe un empaque de 12 u en este producto.' } })));
    component.confirm(chia);
    fixture.detectChanges();

    expect(component.proposals().length).toBe(2);
    expect(text()).toContain('Ya existe un empaque de 12 u en este producto.');
  });

  it('descartar la saca de la lista sin convertirla', () => {
    component.discard(roquin);
    fixture.detectChanges();

    expect(products.discardBox).toHaveBeenCalledWith('box-roq');
    expect(products.convertBox).not.toHaveBeenCalled();
    expect(component.proposals().map(p => p.boxProductId)).toEqual(['box-chia']);
  });

  it('sin cajas por revisar lo dice', () => {
    products.getBoxProposals.mockReturnValue(of([]));
    component.load();
    fixture.detectChanges();
    expect(text()).toContain('No quedan cajas por revisar.');
  });
});
