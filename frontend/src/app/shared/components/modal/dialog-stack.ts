// Dialogs open right now, last one on top. A confirmation opened over a form must take Escape for itself,
// without the form underneath also closing.
const openDialogs: object[] = [];

export function markDialogOpen(dialog: object, open: boolean): void {
  const index = openDialogs.indexOf(dialog);
  if (index >= 0) {
    openDialogs.splice(index, 1);
  }
  if (open) {
    openDialogs.push(dialog);
  }
}

export function isTopDialog(dialog: object): boolean {
  return openDialogs[openDialogs.length - 1] === dialog;
}
