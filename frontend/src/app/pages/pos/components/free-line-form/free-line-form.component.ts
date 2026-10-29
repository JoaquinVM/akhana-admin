import { Component, computed, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Product } from '../../../../core/product/models/product.models';

export interface FreeLine {
  name: string;
  price: number;
}

/**
 * POS-11: choosing a special product asks for the line's name and price, both required.
 * The server validates the final rules.
 */
@Component({
  selector: 'app-free-line-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './free-line-form.component.html',
  styleUrls: ['./free-line-form.component.css']
})
export class FreeLineFormComponent {
  product = input.required<Product>();

  confirmed = output<FreeLine>();
  cancelled = output<void>();

  name = signal<string>('');
  price = signal<number | null>(null);
  submitted = signal<boolean>(false);

  nameError = computed<string | null>(() =>
    this.name().trim().length === 0 ? 'Escriba el nombre.' : null
  );

  priceError = computed<string | null>(() => {
    const price = Number(this.price());
    if (!price || price <= 0) return 'El precio debe ser mayor a 0.';
    if (Math.round(price * 100) !== price * 100) return 'El precio admite hasta 2 decimales.';
    return null;
  });

  ownerLabel = computed<string>(() => {
    const product = this.product();
    return product.specialType === 'THIRD_PARTY'
      ? `Producto de tercero: lo vendido se debe a ${product.ownerCreditorName ?? 'su dueña'}.`
      : 'Producto sin identificar: queda por asignar desde el historial de ventas.';
  });

  confirm(): void {
    this.submitted.set(true);
    if (this.nameError() || this.priceError()) return;
    this.confirmed.emit({ name: this.name().trim(), price: Math.round(Number(this.price()) * 100) / 100 });
  }
}
