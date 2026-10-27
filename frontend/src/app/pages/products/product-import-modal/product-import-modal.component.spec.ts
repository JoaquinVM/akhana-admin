import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ProductImportModalComponent } from './product-import-modal.component';
import { ProductService } from '../../../core/product/product.service';
import { ProductImportResult } from '../../../core/product/models/product-import.models';

describe('ProductImportModalComponent', () => {
  let fixture: ComponentFixture<ProductImportModalComponent>;
  let component: ProductImportModalComponent;
  let productService: any;

  const preview: ProductImportResult = {
    totalRows: 879, importedCount: 780, presentationCount: 0, existingCount: 1, rejectedCount: 2, warningCount: 1,
    newCategories: ['Nutricion'], newSuppliers: ['Alfavitamins'], newTags: [],
    rows: [
      { rowNumber: 5, code: 'FSE-ARA', name: 'Arandano', status: 'REJECTED',
        messages: ['Precio menor a Bs 1: si se vende por peso, marcar en Se vende por Gramo, 100 g o Kilo.'] },
      { rowNumber: 6, code: 'FSE-ARA-MLI', name: 'Arandano 1/2 libra', status: 'REJECTED',
        messages: ['Se rechazó el producto base FSE-ARA (fila 5).'] },
      { rowNumber: 7, code: 'EST-LIP', name: 'Lip care', status: 'WARNING',
        messages: ['Costo 0: toda la venta cuenta como utilidad.'] },
      { rowNumber: 8, code: 'GRA-PSY', name: 'Psylium', status: 'EXISTING',
        messages: ['Ya existe un producto con el código GRA-PSY.'] }
    ]
  };
  const file = new File(['x'], 'Productos.xlsx');

  beforeEach(async () => {
    productService = {
      previewImport: vi.fn().mockReturnValue(of(preview)),
      importProducts: vi.fn().mockReturnValue(of({ ...preview, rows: preview.rows.slice(0, 3) }))
    };
    await TestBed.configureTestingModule({
      imports: [ProductImportModalComponent],
      providers: [{ provide: ProductService, useValue: productService }]
    }).compileComponents();

    fixture = TestBed.createComponent(ProductImportModalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('isOpen', true);
    fixture.detectChanges();
  });

  const selectFile = () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type=file]');
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

  const text = (): string => fixture.nativeElement.textContent;

  it('CAT-06: Revisar muestra el resumen sin guardar y la tabla oculta las ya existentes', () => {
    expect(component.canImport()).toBe(false);
    selectFile();
    component.review();
    fixture.detectChanges();

    expect(productService.previewImport).toHaveBeenCalledWith(file);
    expect(productService.importProducts).not.toHaveBeenCalled();
    expect(text()).toContain('todavía no se guardó nada');
    expect(text()).toContain('productos a importar');
    expect(text()).toContain('Alfavitamins');
    expect(component.visibleRows().map(r => r.rowNumber)).toEqual([5, 6, 7]);
    expect(component.canImport()).toBe(true);

    component.filter.set('EXISTING');
    expect(component.visibleRows().map(r => r.rowNumber)).toEqual([8]);
    component.filter.set('WARNING');
    expect(component.visibleRows().map(r => r.rowNumber)).toEqual([7]);
  });

  it('CAT-06: Importar guarda el mismo archivo, avisa a la página y no deja importar dos veces', () => {
    const imported = vi.fn();
    component.imported.subscribe(imported);
    selectFile();
    component.review();
    component.import();
    fixture.detectChanges();

    expect(productService.importProducts).toHaveBeenCalledWith(file);
    expect(imported).toHaveBeenCalledTimes(1);
    expect(component.isImported()).toBe(true);
    expect(component.canImport()).toBe(false);
    expect(text()).toContain('Importación terminada');
    expect(text()).toContain('productos importados');
  });

  it('CAT-06: elegir otro archivo obliga a revisar de nuevo', () => {
    selectFile();
    component.review();
    selectFile();
    expect(component.result()).toBeNull();
    expect(component.canImport()).toBe(false);
  });

  it('CAT-06: muestra el error del archivo que devuelve el servidor', () => {
    productService.previewImport.mockReturnValue(throwError(() => ({
      error: { message: 'Faltan las columnas: Costo.' }
    })));
    selectFile();
    component.review();
    fixture.detectChanges();
    expect(text()).toContain('Faltan las columnas: Costo.');
    expect(component.result()).toBeNull();
  });

  it('CAT-06: descarga las rechazadas como .xlsx con fila, código, nombre y motivo', async () => {
    URL.createObjectURL = vi.fn().mockReturnValue('blob:rejected');
    URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('productos-rechazados.xlsx');
    });
    selectFile();
    component.review();
    component.downloadRejected();

    expect(click).toHaveBeenCalled();
    const blob: Blob = (URL.createObjectURL as any).mock.calls[0][0];
    const content = new TextDecoder().decode(await blob.arrayBuffer());
    expect(content).toContain('Se rechazó el producto base FSE-ARA (fila 5).');
    expect(content).toContain('FSE-ARA-MLI');
    expect(content).not.toContain('EST-LIP');
    expect(content).not.toContain('GRA-PSY');
    click.mockRestore();
  });

  it('CAT-06: al cerrar limpia el resultado', () => {
    const closed = vi.fn();
    component.close.subscribe(closed);
    selectFile();
    component.review();
    component.onClose();
    expect(closed).toHaveBeenCalled();
    expect(component.result()).toBeNull();
    expect(component.file()).toBeNull();
  });
});
