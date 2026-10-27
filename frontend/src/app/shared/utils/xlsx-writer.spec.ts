import { buildXlsx } from './xlsx-writer';

describe('buildXlsx', () => {
  it('arma un ZIP con la hoja, texto escapado y números como número', async () => {
    const blob = buildXlsx('Rechazadas', [['Fila', 'Motivo'], [12, 'Costo <0> & "más"']]);
    const bytes = new Uint8Array(await blob.arrayBuffer());
    const content = new TextDecoder().decode(bytes);

    expect(String.fromCharCode(bytes[0], bytes[1])).toBe('PK');
    expect(blob.type).toBe('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    expect(content).toContain('xl/worksheets/sheet1.xml');
    expect(content).toContain('<sheet name="Rechazadas"');
    expect(content).toContain('<c r="A2"><v>12</v></c>');
    expect(content).toContain('Costo &lt;0&gt; &amp; &quot;más&quot;');
  });
});
