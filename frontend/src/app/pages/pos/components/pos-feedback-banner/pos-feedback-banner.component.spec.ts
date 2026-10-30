import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PosFeedbackBannerComponent } from './pos-feedback-banner.component';

describe('PosFeedbackBannerComponent', () => {
  let fixture: ComponentFixture<PosFeedbackBannerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PosFeedbackBannerComponent] }).compileComponents();
    fixture = TestBed.createComponent(PosFeedbackBannerComponent);
  });

  it('muestra el texto con el estilo de su tipo', () => {
    fixture.componentRef.setInput('message', { type: 'error', text: 'Caja cerrada' });
    fixture.detectChanges();
    const banner: HTMLElement = fixture.nativeElement.querySelector('.feedback-banner');
    expect(banner.classList.contains('error')).toBe(true);
    expect(banner.classList.contains('success')).toBe(false);
    expect(banner.textContent).toContain('Caja cerrada');
  });

  it('emite dismiss al cerrar', () => {
    fixture.componentRef.setInput('message', { type: 'success', text: 'Listo' });
    fixture.detectChanges();
    let dismissed = false;
    fixture.componentInstance.dismiss.subscribe(() => (dismissed = true));
    fixture.nativeElement.querySelector('.btn-close-feedback').click();
    expect(dismissed).toBe(true);
  });
});
