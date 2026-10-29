import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModalComponent } from '../../../../shared/components/modal/modal.component';
import { VoidReasonModalComponent } from '../../../purchases/components/void-reason-modal/void-reason-modal.component';
import { ConsumptionService } from '../../../../core/consumption/consumption.service';
import { Consumption } from '../../../../core/consumption/models/consumption.models';
import { StoreDayPipe } from '../../../../shared/utils/store-day.pipe';

// CON-01: read-only detail with lines and audit; the void does not depend on the cash session
@Component({
  selector: 'app-consumption-detail-modal',
  standalone: true,
  imports: [StoreDayPipe, CommonModule, ModalComponent, VoidReasonModalComponent],
  templateUrl: './consumption-detail-modal.component.html',
  styleUrls: ['./consumption-detail-modal.component.css']
})
export class ConsumptionDetailModalComponent {
  private readonly consumptionService = inject(ConsumptionService);

  consumptionId = input<string | null>(null);

  // Emits the voided consumption to refresh the list
  updated = output<Consumption>();
  close = output<void>();

  consumption = signal<Consumption | null>(null);
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  isVoidOpen = signal<boolean>(false);
  voidError = signal<string | null>(null);
  isVoiding = signal<boolean>(false);

  isActive = computed(() => this.consumption()?.status === 'REGISTRADO');

  constructor() {
    effect(() => {
      const id = this.consumptionId();
      if (id) {
        this.load(id);
      } else {
        this.consumption.set(null);
      }
    });
  }

  private load(id: string): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.consumptionService.getConsumptionById(id).subscribe({
      next: consumption => {
        this.consumption.set(consumption);
        this.isLoading.set(false);
      },
      error: err => {
        this.errorMessage.set(err.error?.message || 'No se pudo cargar el consumo.');
        this.isLoading.set(false);
      }
    });
  }

  openVoid(): void {
    this.voidError.set(null);
    this.isVoidOpen.set(true);
  }

  closeVoid(): void {
    if (!this.isVoiding()) {
      this.isVoidOpen.set(false);
    }
  }

  confirmVoid(voidReason: string): void {
    const consumption = this.consumption();
    if (!consumption) {
      return;
    }
    this.isVoiding.set(true);
    this.voidError.set(null);
    this.consumptionService.voidConsumption(consumption.id, { voidReason }).subscribe({
      next: updated => {
        this.consumption.set(updated);
        this.isVoiding.set(false);
        this.isVoidOpen.set(false);
        this.updated.emit(updated);
      },
      error: err => {
        this.voidError.set(err.error?.message || 'No se pudo anular.');
        this.isVoiding.set(false);
      }
    });
  }

  onClose(): void {
    if (!this.isVoiding()) {
      this.close.emit();
    }
  }
}
