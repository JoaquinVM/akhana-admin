import { TestBed } from '@angular/core/testing';
import { IconComponent, IconName } from './icon.component';

describe('IconComponent', () => {
  it('dibuja cada ícono como SVG', async () => {
    await TestBed.configureTestingModule({ imports: [IconComponent] }).compileComponents();
    const names: IconName[] = ['close', 'tag', 'package', 'chart', 'receipt', 'users', 'lock', 'unlock', 'bag', 'coins',
      'inbox', 'check-circle', 'handshake', 'star', 'cash', 'qr', 'leaf'];
    for (const name of names) {
      const fixture = TestBed.createComponent(IconComponent);
      fixture.componentRef.setInput('name', name);
      fixture.detectChanges();
      const svg: SVGElement = fixture.nativeElement.querySelector('svg');
      expect(svg.children.length, name).toBeGreaterThan(0);
    }
  });
});
