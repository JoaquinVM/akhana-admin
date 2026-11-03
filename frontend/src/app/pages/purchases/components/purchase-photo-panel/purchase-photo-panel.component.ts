import { Component, DestroyRef, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { PurchaseReadingService } from '../../../../core/purchase/purchase-reading.service';
import { reducePhoto } from '../../../../shared/utils/reduce-photo';

// COM-23: the invoice photo in the detail of a purchase, also to add it if its upload failed when registering
@Component({
  selector: 'app-purchase-photo-panel',
  standalone: true,
  templateUrl: './purchase-photo-panel.component.html',
  styleUrls: ['./purchase-photo-panel.component.css']
})
export class PurchasePhotoPanelComponent {
  private readonly readingService = inject(PurchaseReadingService);
  private readonly destroyRef = inject(DestroyRef);

  purchaseId = input.required<string>();
  photoId = input<string | null>(null);
  editable = input<boolean>(false);

  changed = output<void>();

  url = signal<string | null>(null);
  isUploading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);
  private request: Subscription | null = null;

  private readonly load = effect(() => {
    const photoId = this.photoId();
    const purchaseId = this.purchaseId();
    untracked(() => this.clear());
    if (!photoId) {
      return;
    }
    this.request = this.readingService.getPhotoImage(purchaseId, photoId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: blob => this.url.set(URL.createObjectURL(blob)),
        error: () => this.errorMessage.set('No se pudo cargar la foto.')
      });
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.clear());
  }

  async onFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.isUploading.set(true);
    this.errorMessage.set(null);
    this.readingService.uploadPhoto(this.purchaseId(), await reducePhoto(file)).subscribe({
      next: () => {
        this.isUploading.set(false);
        this.changed.emit();
      },
      error: err => {
        this.isUploading.set(false);
        this.errorMessage.set(err.error?.message || 'No se pudo guardar la foto. Intenta de nuevo.');
      }
    });
  }

  private clear(): void {
    this.request?.unsubscribe();
    this.request = null;
    const current = this.url();
    if (current) {
      URL.revokeObjectURL(current);
    }
    this.url.set(null);
    this.errorMessage.set(null);
  }
}
