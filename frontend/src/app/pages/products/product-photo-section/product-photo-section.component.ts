import { Component, DestroyRef, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { ProductPhotoService } from '../../../core/product/product-photo.service';
import { ConfirmModalComponent } from '../../../shared/components/confirm-modal/confirm-modal.component';

export const INVALID_PHOTO_MESSAGE = 'La foto debe ser JPEG, PNG o WebP de hasta 10 MB.';
export const SAVE_PHOTO_FAILED_MESSAGE = 'No se pudo guardar la foto. Intenta de nuevo.';

// CAT-12: the photo saves on its own, apart from the product form and its Guardar button
@Component({
  selector: 'app-product-photo-section',
  standalone: true,
  imports: [ConfirmModalComponent],
  templateUrl: './product-photo-section.component.html',
  styleUrls: ['./product-photo-section.component.css']
})
export class ProductPhotoSectionComponent {
  private readonly photoService = inject(ProductPhotoService);
  private readonly destroyRef = inject(DestroyRef);

  // Null while the product is being created
  productId = input<string | null>(null);
  photoId = input<string | null | undefined>(null);
  // Says which product it was, because the form may be showing another one when an upload ends
  photoChange = output<{ productId: string; photoId: string | null }>();

  imageUrl = signal<string | null>(null);
  imageFailed = signal<boolean>(false);
  isBusy = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  isConfirmRemoveOpen = signal<boolean>(false);
  private request: Subscription | null = null;
  private shownProductId: string | null = null;

  // The section outlives the modal, so opening or closing the form starts it clean
  private readonly load = effect(() => {
    const productId = this.productId();
    const photoId = this.photoId();
    untracked(() => {
      this.clearImage();
      if (productId !== this.shownProductId) {
        this.shownProductId = productId;
        this.isBusy.set(false);
        this.errorMessage.set(null);
        this.isConfirmRemoveOpen.set(false);
      }
    });
    if (!productId || !photoId) {
      return;
    }
    this.request = this.photoService.getImage(productId, photoId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => this.imageUrl.set(URL.createObjectURL(blob)),
        error: () => this.imageFailed.set(true)
      });
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.clearImage());
  }

  onFileSelected(event: Event): void {
    const field = event.target as HTMLInputElement;
    const file = field.files?.[0];
    // Lets the same file be chosen again after an error
    field.value = '';
    const productId = this.productId();
    if (!file || !productId) {
      return;
    }
    this.isBusy.set(true);
    this.errorMessage.set(null);
    this.photoService.upload(productId, file, this.photoId() ?? null)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.endIfShown(productId);
          this.photoChange.emit({ productId, photoId: response.photoId });
        },
        error: (err: HttpErrorResponse) => {
          if (this.endIfShown(productId)) {
            this.errorMessage.set(this.messageOf(err));
          }
        }
      });
  }

  confirmRemove(): void {
    const productId = this.productId();
    const photoId = this.photoId();
    if (!productId || !photoId) {
      return;
    }
    this.isBusy.set(true);
    this.errorMessage.set(null);
    this.photoService.remove(productId, photoId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          if (this.endIfShown(productId)) {
            this.isConfirmRemoveOpen.set(false);
          }
          this.photoChange.emit({ productId, photoId: null });
        },
        error: (err: HttpErrorResponse) => {
          if (this.endIfShown(productId)) {
            this.isConfirmRemoveOpen.set(false);
            this.errorMessage.set(this.messageOf(err));
          }
        }
      });
  }

  // A request that ends after the form moved on must not touch what the form shows now
  private endIfShown(productId: string): boolean {
    if (productId !== this.productId()) {
      return false;
    }
    this.isBusy.set(false);
    return true;
  }

  // nginx rejects what is over its own limit before the backend can answer
  private messageOf(err: HttpErrorResponse): string {
    if (err.status === 413) {
      return INVALID_PHOTO_MESSAGE;
    }
    return err.error?.message || SAVE_PHOTO_FAILED_MESSAGE;
  }

  private clearImage(): void {
    this.request?.unsubscribe();
    this.request = null;
    const current = this.imageUrl();
    if (current) {
      URL.revokeObjectURL(current);
      this.imageUrl.set(null);
    }
    this.imageFailed.set(false);
  }
}
