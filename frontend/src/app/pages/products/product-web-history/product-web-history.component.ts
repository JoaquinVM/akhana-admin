import { Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ProductWebPublicationChange } from '../../../core/product/models/product.models';
import { ProductService } from '../../../core/product/product.service';

// CAT-13: who marked or unmarked the product for the web, and when; shown inside the product audit
@Component({
  selector: 'app-product-web-history',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './product-web-history.component.html',
  styleUrls: ['./product-web-history.component.css']
})
export class ProductWebHistoryComponent implements OnInit {
  private readonly productService = inject(ProductService);

  productId = input.required<string>();

  changes = signal<ProductWebPublicationChange[]>([]);
  isLoading = signal<boolean>(true);
  hasFailed = signal<boolean>(false);

  ngOnInit(): void {
    this.productService.getWebPublicationHistory(this.productId()).subscribe({
      next: (changes) => {
        this.changes.set(changes);
        this.isLoading.set(false);
      },
      error: () => {
        this.hasFailed.set(true);
        this.isLoading.set(false);
      }
    });
  }
}
