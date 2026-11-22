import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { InventoryService } from '../../../core/inventory/inventory.service';
import { StockCount } from '../../../core/inventory/models/inventory.models';
import { CategoryService } from '../../../core/category/category.service';
import { Category } from '../../../core/category/models/category.models';
import { IconComponent } from '../../../shared/components/icon/icon.component';

/** INV-05, INV-18: the counts, open and closed, and a new one by category or of loose products. */
@Component({
  selector: 'app-counts',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  templateUrl: './counts.component.html',
  styleUrls: ['../inventory-page.css', './counts.component.css']
})
export class CountsComponent implements OnInit {
  private readonly inventoryService = inject(InventoryService);
  private readonly categoryService = inject(CategoryService);
  private readonly router = inject(Router);

  readonly counts = signal<StockCount[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly categoryId = signal<string>('');
  readonly isLoading = signal(true);
  readonly isCreating = signal(false);
  readonly errorMessage = signal<string | null>(null);

  ngOnInit(): void {
    this.categoryService.getCategories(undefined, 'ACTIVO').subscribe({
      next: categories => this.categories.set(categories),
      error: () => this.categories.set([])
    });
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.inventoryService.getCounts().subscribe({
      next: counts => {
        this.counts.set(counts);
        this.isLoading.set(false);
      },
      error: err => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || 'No se pudieron cargar los conteos.');
      }
    });
  }

  create(): void {
    this.isCreating.set(true);
    this.errorMessage.set(null);
    this.inventoryService.createCount(this.categoryId() || null).subscribe({
      next: count => {
        this.isCreating.set(false);
        this.router.navigate(['/inventory/counts', count.id]);
      },
      error: err => {
        this.isCreating.set(false);
        this.errorMessage.set(err.error?.message || 'No se pudo empezar el conteo.');
      }
    });
  }

  open(count: StockCount): void {
    this.router.navigate(['/inventory/counts', count.id]);
  }

  statusLabel(count: StockCount): string {
    return count.status === 'ABIERTO' ? 'Abierto' : count.status === 'CONFIRMADO' ? 'Confirmado' : 'Descartado';
  }
}
