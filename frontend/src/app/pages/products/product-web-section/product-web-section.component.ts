import { Component, inject, input, output, signal } from '@angular/core';
import { Product } from '../../../core/product/models/product.models';
import { ProductService } from '../../../core/product/product.service';

// CAT-13: saves on its own, like the photo. Marking is not publishing: the product also has to be active with a photo
@Component({
  selector: 'app-product-web-section',
  standalone: true,
  templateUrl: './product-web-section.component.html',
  styleUrls: ['./product-web-section.component.css']
})
export class ProductWebSectionComponent {
  private readonly productService = inject(ProductService);

  productId = input.required<string>();
  published = input<boolean>(false);
  hasPhoto = input<boolean>(false);
  active = input<boolean>(true);
  publishedChange = output<Product>();

  isBusy = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  toggle(event: Event): void {
    const field = event.target as HTMLInputElement;
    const request = field.checked
      ? this.productService.markForWeb(this.productId())
      : this.productService.unmarkForWeb(this.productId());
    this.isBusy.set(true);
    this.errorMessage.set(null);
    request.subscribe({
      next: (product) => {
        this.isBusy.set(false);
        this.publishedChange.emit(product);
      },
      error: (err) => {
        this.isBusy.set(false);
        field.checked = this.published();
        this.errorMessage.set(err.error?.message || 'No se pudo guardar el cambio. Intenta de nuevo.');
      }
    });
  }
}
