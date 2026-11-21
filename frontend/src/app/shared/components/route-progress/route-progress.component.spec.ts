import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouteProgressComponent } from './route-progress.component';

@Component({ template: '' })
class ScreenStub {}

describe('RouteProgressComponent', () => {
  it('muestra la línea solo mientras se abre una pantalla', async () => {
    let release: (() => void) | undefined;
    TestBed.configureTestingModule({
      imports: [RouteProgressComponent],
      providers: [provideRouter([
        { path: 'pos', component: ScreenStub },
        { path: 'slow', component: ScreenStub, canActivate: [() => new Promise<boolean>(r => (release = () => r(true)))] }
      ])]
    });
    const fixture = TestBed.createComponent(RouteProgressComponent);
    const router = TestBed.inject(Router);
    const bar = () => (fixture.detectChanges(), fixture.nativeElement.querySelector('.route-progress'));

    expect(bar()).toBeNull();
    const done = router.navigateByUrl('/slow');
    await vi.waitFor(() => expect(release).toBeDefined());
    expect(bar()).not.toBeNull();
    release!();
    await done;
    expect(bar()).toBeNull();
  });
});
