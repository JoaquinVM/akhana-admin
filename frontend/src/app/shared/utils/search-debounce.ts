import { DestroyRef, inject } from '@angular/core';

/** Time a search box waits after the last key before asking the server. */
export const SEARCH_DEBOUNCE_MS = 250;

/**
 * Returns a scheduler that runs the search only once the user stops typing for SEARCH_DEBOUNCE_MS.
 * A new key cancels the pending one, and leaving the screen cancels it too. Call it in an injection context.
 */
export function searchDebouncer(): (run: () => void) => void {
  let timer: ReturnType<typeof setTimeout> | undefined;
  inject(DestroyRef).onDestroy(() => clearTimeout(timer));
  return run => {
    clearTimeout(timer);
    timer = setTimeout(run, SEARCH_DEBOUNCE_MS);
  };
}
