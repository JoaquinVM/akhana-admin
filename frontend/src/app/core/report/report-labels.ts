const MONTH_NAMES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre',
  'octubre', 'noviembre', 'diciembre'];

/** "2026-07" → "julio 2026" */
/** 096: "2026-07" → "Julio 2026", for a month that stands alone at the start of a cell or a header */
export function monthTitle(month: string): string {
  const label = monthLabel(month);
  return label.charAt(0).toUpperCase() + label.slice(1);
}

export function monthLabel(month: string): string {
  const [year, number] = month.split('-');
  return `${MONTH_NAMES[Number(number) - 1]} ${year}`;
}
