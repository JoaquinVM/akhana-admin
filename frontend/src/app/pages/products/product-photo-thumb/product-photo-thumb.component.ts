import { AfterViewInit, Component, DestroyRef, ElementRef, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { ProductPhotoService } from '../../../core/product/product-photo.service';

// CAT-12: a row's thumbnail, requested only when the row comes into view. Without an image, or if it fails, «Sin foto»
@Component({
  selector: 'app-product-photo-thumb',
  standalone: true,
  template: `
    @if (url(); as src) {
      <img class="photo-thumb" [src]="src" alt="Foto del producto" />
    } @else if (!photoId() || failed()) {
      <span class="photo-thumb-empty">Sin foto</span>
    } @else {
      <span class="photo-thumb-loading" aria-hidden="true"></span>
    }
  `,
  styleUrls: ['./product-photo-thumb.component.css']
})
export class ProductPhotoThumbComponent implements AfterViewInit {
  private readonly photoService = inject(ProductPhotoService);
  private readonly element = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);

  productId = input.required<string>();
  photoId = input<string | null | undefined>(null);

  url = signal<string | null>(null);
  failed = signal<boolean>(false);
  private visible = signal<boolean>(false);
  private request: Subscription | null = null;

  private readonly load = effect(() => {
    const photoId = this.photoId();
    const productId = this.productId();
    const visible = this.visible();
    untracked(() => this.clear());
    if (!photoId || !visible) {
      return;
    }
    this.request = this.photoService.getThumbnail(productId, photoId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => this.url.set(URL.createObjectURL(blob)),
        error: () => this.failed.set(true)
      });
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.clear());
  }

  ngAfterViewInit(): void {
    if (typeof IntersectionObserver === 'undefined') {
      this.visible.set(true);
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        observer.disconnect();
        this.visible.set(true);
      }
    });
    observer.observe(this.element.nativeElement);
    this.destroyRef.onDestroy(() => observer.disconnect());
  }

  private clear(): void {
    this.request?.unsubscribe();
    this.request = null;
    const current = this.url();
    if (current) {
      URL.revokeObjectURL(current);
      this.url.set(null);
    }
    this.failed.set(false);
  }
}
