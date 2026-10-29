import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ToastComponent } from './toast.component';
import { TOAST_DURATION_MS, ToastService } from './toast.service';

describe('ToastComponent', () => {
  let fixture: ComponentFixture<ToastComponent>;
  let service: ToastService;

  beforeEach(async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({ imports: [ToastComponent] }).compileComponents();
    fixture = TestBed.createComponent(ToastComponent);
    service = TestBed.inject(ToastService);
  });

  afterEach(() => vi.useRealTimers());

  it('muestra el aviso, se va solo y se puede cerrar', () => {
    service.success('Producto guardado.');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Producto guardado.');

    vi.advanceTimersByTime(TOAST_DURATION_MS);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.toast')).toBeNull();

    service.error('No se pudo guardar.');
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.toast-close').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.toast')).toBeNull();
  });

  it('muestra como mucho tres avisos', () => {
    ['a', 'b', 'c', 'd'].forEach((t) => service.success(t));
    expect(service.toasts().map((t) => t.text)).toEqual(['b', 'c', 'd']);
  });
});
