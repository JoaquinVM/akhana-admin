import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BoxProposal } from '../../../core/product/models/product.models';
import { ProductService } from '../../../core/product/product.service';
import { ToastService } from '../../../shared/components/toast/toast.service';

// What the person may correct before confirming a box (COM-10)
export interface BoxDraft {
  name: string;
  baseQuantity: number | null;
  fixedPrice: number | null;
  barcode: string;
}

function draftOf(proposal: BoxProposal): BoxDraft {
  return {
    name: proposal.proposedName,
    baseQuantity: proposal.proposedBaseQuantity,
    fixedPrice: proposal.proposedPrice,
    barcode: proposal.boxBarcodes[0] ?? ''
  };
}

/**
 * COM-10: the boxes that the V1 sells as their own product become packages of their base product (CAT-15). The system
 * proposes; a person confirms or discards each one, and nothing changes until then.
 */
@Component({
  selector: 'app-box-conversion',
  standalone: true,
  imports: [DecimalPipe, RouterLink],
  templateUrl: './box-conversion.component.html',
  styleUrls: ['./box-conversion.component.css']
})
export class BoxConversionComponent implements OnInit {
  private readonly productService = inject(ProductService);
  private readonly toast = inject(ToastService);

  readonly proposals = signal<BoxProposal[]>([]);
  readonly drafts = signal<Record<string, BoxDraft>>({});
  readonly isLoading = signal<boolean>(true);
  readonly loadError = signal<string | null>(null);
  readonly savingId = signal<string | null>(null);
  // The server's message for the box it refused, shown on that box
  readonly errors = signal<Record<string, string>>({});

  readonly remaining = computed(() => this.proposals().length);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.isLoading.set(true);
    this.loadError.set(null);
    this.productService.getBoxProposals().subscribe({
      next: (proposals) => {
        this.proposals.set(proposals);
        this.drafts.set(Object.fromEntries(proposals.map((p) => [p.boxProductId, draftOf(p)])));
        this.isLoading.set(false);
      },
      error: () => {
        this.loadError.set('No se pudo cargar la lista de cajas.');
        this.isLoading.set(false);
      }
    });
  }

  draft(proposal: BoxProposal): BoxDraft {
    return this.drafts()[proposal.boxProductId] ?? draftOf(proposal);
  }

  update(proposal: BoxProposal, field: keyof BoxDraft, value: string): void {
    const parsed = field === 'name' || field === 'barcode' ? value : value === '' ? null : Number(value);
    this.drafts.update((all) => ({ ...all, [proposal.boxProductId]: { ...this.draft(proposal), [field]: parsed } }));
  }

  // What the package will cost: equivalence x cost of the base product, as the catalog computes it (CAT-15)
  packageCost(proposal: BoxProposal): number | null {
    const quantity = this.draft(proposal).baseQuantity;
    return quantity ? Math.round(quantity * proposal.baseBuyPrice * 100) / 100 : null;
  }

  confirm(proposal: BoxProposal): void {
    const draft = this.draft(proposal);
    this.savingId.set(proposal.boxProductId);
    this.setError(proposal, null);
    this.productService.convertBox(proposal.boxProductId, {
      name: draft.name.trim(),
      baseQuantity: Number(draft.baseQuantity),
      fixedPrice: draft.fixedPrice ? Number(draft.fixedPrice) : null,
      barcode: draft.barcode || null
    }).subscribe({
      next: () => {
        this.savingId.set(null);
        this.remove(proposal);
        this.toast.success(`${proposal.boxName} pasó a ser ${draft.name.trim()} de ${proposal.baseName}.`);
      },
      error: (err) => {
        this.savingId.set(null);
        this.setError(proposal, err.error?.message || 'No se pudo convertir la caja.');
      }
    });
  }

  discard(proposal: BoxProposal): void {
    this.savingId.set(proposal.boxProductId);
    this.setError(proposal, null);
    this.productService.discardBox(proposal.boxProductId).subscribe({
      next: () => {
        this.savingId.set(null);
        this.remove(proposal);
      },
      error: (err) => {
        this.savingId.set(null);
        this.setError(proposal, err.error?.message || 'No se pudo descartar la caja.');
      }
    });
  }

  private remove(proposal: BoxProposal): void {
    this.proposals.update((list) => list.filter((p) => p.boxProductId !== proposal.boxProductId));
  }

  private setError(proposal: BoxProposal, message: string | null): void {
    this.errors.update((all) => {
      const next = { ...all };
      if (message) next[proposal.boxProductId] = message;
      else delete next[proposal.boxProductId];
      return next;
    });
  }
}
