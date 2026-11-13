import { Injectable } from '@angular/core';

// CAJ-15: what is written in the closing modal, kept in this browser until the cash session closes
export interface CloseCashDraft {
  sessionId: string;
  mode: 'ARQUEO' | 'DIRECTO';
  cuts: { denomination: number; cashQuantity: number; reserveQuantity: number }[];
  reserveBills: number;
  reserveCoins: number;
  closingAmount: number;
  closingComment: string;
}

const KEY = 'akhana.closeCashDraft';

function amount(value: unknown): number {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? Math.round(n * 100) / 100 : 0;
}

function quantity(value: unknown): number {
  const n = Number(value);
  return Number.isFinite(n) && n > 0 ? Math.floor(n) : 0;
}

@Injectable({ providedIn: 'root' })
export class CloseCashDraftService {
  // Only the draft of this session: one left by a session closed elsewhere is dropped
  load(sessionId: string): CloseCashDraft | null {
    try {
      const raw = localStorage.getItem(KEY);
      if (!raw) {
        return null;
      }
      const draft = JSON.parse(raw);
      if (!draft || draft.sessionId !== sessionId) {
        localStorage.removeItem(KEY);
        return null;
      }
      return {
        sessionId,
        mode: draft.mode === 'DIRECTO' ? 'DIRECTO' : 'ARQUEO',
        cuts: Array.isArray(draft.cuts)
          ? draft.cuts.map((c: any) => ({
              denomination: Number(c?.denomination),
              cashQuantity: quantity(c?.cashQuantity),
              reserveQuantity: quantity(c?.reserveQuantity)
            }))
          : [],
        reserveBills: amount(draft.reserveBills),
        reserveCoins: amount(draft.reserveCoins),
        closingAmount: amount(draft.closingAmount),
        closingComment: typeof draft.closingComment === 'string' ? draft.closingComment : ''
      };
    } catch {
      return null;
    }
  }

  save(draft: CloseCashDraft): void {
    try {
      localStorage.setItem(KEY, JSON.stringify(draft));
    } catch {
      // Without storage the count still works; it is just not kept
    }
  }

  clear(): void {
    try {
      localStorage.removeItem(KEY);
    } catch {
      // Nothing to clear
    }
  }
}
