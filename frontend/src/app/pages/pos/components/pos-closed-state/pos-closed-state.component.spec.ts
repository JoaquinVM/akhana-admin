import { TestBed } from '@angular/core/testing';
import { PosClosedStateComponent } from './pos-closed-state.component';

describe('PosClosedStateComponent', () => {
  it('emite openCash al pulsar «Abrir caja»', async () => {
    await TestBed.configureTestingModule({ imports: [PosClosedStateComponent] }).compileComponents();
    const fixture = TestBed.createComponent(PosClosedStateComponent);
    fixture.detectChanges();
    let opened = false;
    fixture.componentInstance.openCash.subscribe(() => (opened = true));
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('.closed-hero-actions button');
    expect(button.textContent).toContain('Abrir caja');
    button.click();
    expect(opened).toBe(true);
  });
});
