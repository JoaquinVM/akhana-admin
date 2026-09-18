/**
 * =========================================================================
 * AKHANA ADMIN POS - GOOGLE APPS SCRIPT BACKEND (Code.gs)
 * =========================================================================
 * Servidor Webhook / REST API para sincronización de ventas, productos y
 * arqueos de caja en Google Sheets.
 *
 * Configuración en Google Sheets:
 * 1. Crea una hoja de cálculo en Google Drive llamada "Akhana Admin Database".
 * 2. Ve a Extensiones > Apps Script.
 * 3. Pega este código en Code.gs.
 * 4. Haz clic en "Implementar" > "Nueva implementación" > Tipo: "Aplicación web".
 * 5. Ejecutar como: "Yo", Quién tiene acceso: "Cualquier usuario" (Anyone).
 * 6. Copia la URL generada y pégala en la configuración del POS Akhana.
 * =========================================================================
 */

const SHEET_NAMES = {
  VENTAS: 'Ventas',
  DETALLE_VENTAS: 'Detalle_Ventas',
  PRODUCTOS: 'Productos',
  CAJAS: 'Cajas'
};

function doGet(e) {
  try {
    const action = (e && e.parameter && e.parameter.action) || 'ping';
    const ss = SpreadsheetApp.getActiveSpreadsheet();

    if (action === 'ping') {
      return jsonResponse({ status: 'success', message: 'Akhana POS Apps Script Online', time: new Date() });
    }

    if (action === 'get_products') {
      const sheet = getOrCreateSheet(ss, SHEET_NAMES.PRODUCTOS, [
        'ID', 'Codigo', 'CodigoBarras', 'Nombre', 'Grupo', 'Proveedor', 'Costo', 'PrecioVenta', 'TipoVenta', 'EsFrecuente', 'Activo'
      ]);
      const data = sheet.getDataRange().getValues();
      const headers = data[0];
      const rows = data.slice(1).map(r => {
        let obj = {};
        headers.forEach((h, i) => obj[h] = r[i]);
        return obj;
      });
      return jsonResponse({ status: 'success', products: rows });
    }

    if (action === 'get_sales') {
      const sheet = getOrCreateSheet(ss, SHEET_NAMES.VENTAS, [
        'ID', 'Orden', 'Fecha', 'Cajero', 'Cliente', 'Subtotal', 'Descuentos', 'TotalNeto', 'MetodoPago', 'Estado', 'MotivoAnulacion'
      ]);
      const data = sheet.getDataRange().getValues();
      const headers = data[0];
      const rows = data.slice(1).map(r => {
        let obj = {};
        headers.forEach((h, i) => obj[h] = r[i]);
        return obj;
      });
      return jsonResponse({ status: 'success', sales: rows });
    }

    return jsonResponse({ status: 'error', message: 'Acción GET desconocida' });
  } catch (err) {
    return jsonResponse({ status: 'error', message: err.toString() });
  }
}

function doPost(e) {
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return jsonResponse({ status: 'error', message: 'Cuerpo de solicitud vacío' });
    }

    const payload = JSON.parse(e.postData.contents);
    const action = payload.action;
    const ss = SpreadsheetApp.getActiveSpreadsheet();

    if (action === 'sync_sale') {
      const sale = payload.sale;
      const salesSheet = getOrCreateSheet(ss, SHEET_NAMES.VENTAS, [
        'ID', 'Orden', 'Fecha', 'Cajero', 'Cliente', 'Subtotal', 'Descuentos', 'TotalNeto', 'MetodoPago', 'Estado', 'MotivoAnulacion'
      ]);

      // Insertar Venta
      salesSheet.appendRow([
        sale.id,
        sale.orderNumber,
        sale.createdAt,
        sale.cashierName,
        sale.customerName,
        sale.grossSubtotal,
        sale.totalDiscounts,
        sale.netTotal,
        sale.paymentMethod,
        sale.status,
        sale.voidReason || ''
      ]);

      // Insertar Detalle de Venta
      const detailsSheet = getOrCreateSheet(ss, SHEET_NAMES.DETALLE_VENTAS, [
        'VentaID', 'Orden', 'ProductoID', 'Codigo', 'Nombre', 'Tipo', 'Cantidad', 'PrecioUnitario', 'Descuento', 'Subtotal'
      ]);

      if (sale.items && sale.items.length > 0) {
        sale.items.forEach(item => {
          detailsSheet.appendRow([
            sale.id,
            sale.orderNumber,
            item.productId,
            item.productCode,
            item.productName,
            item.saleType,
            item.quantity,
            item.unitPrice,
            item.discount,
            item.finalSubtotal
          ]);
        });
      }

      return jsonResponse({ status: 'success', message: 'Venta sincronizada correctamente', orderNumber: sale.orderNumber });
    }

    if (action === 'sync_register_close') {
      const reg = payload.register;
      const regSheet = getOrCreateSheet(ss, SHEET_NAMES.CAJAS, [
        'ID', 'Caja', 'Apertura', 'Cierre', 'CajeroApertura', 'CajeroCierre', 'MontoInicial', 'VentasEfectivo', 'VentasQR', 'EfectivoEsperado', 'EfectivoContado', 'Diferencia', 'Notas'
      ]);

      regSheet.appendRow([
        reg.id,
        reg.registerNumber,
        reg.openedAt,
        reg.closedAt,
        reg.openedBy,
        reg.closedBy,
        reg.initialAmount,
        reg.cashSalesTotal,
        reg.qrSalesTotal,
        reg.expectedCash,
        reg.countedCash,
        reg.difference,
        reg.notes || ''
      ]);

      return jsonResponse({ status: 'success', message: 'Arqueo de caja registrado correctamente' });
    }

    if (action === 'void_sale') {
      const { saleId, voidReason, voidedBy } = payload;
      const salesSheet = getOrCreateSheet(ss, SHEET_NAMES.VENTAS, []);
      const data = salesSheet.getDataRange().getValues();

      for (let i = 1; i < data.length; i++) {
        if (data[i][0] === saleId) {
          // Columna 10 (Estado) y 11 (Motivo)
          salesSheet.getRange(i + 1, 10).setValue('ANULADA');
          salesSheet.getRange(i + 1, 11).setValue('Anulado por ' + voidedBy + ': ' + voidReason);
          return jsonResponse({ status: 'success', message: 'Venta marcada como anulada en hoja' });
        }
      }
      return jsonResponse({ status: 'error', message: 'Venta no encontrada en la hoja' });
    }

    return jsonResponse({ status: 'error', message: 'Acción POST no reconocida' });
  } catch (err) {
    return jsonResponse({ status: 'error', message: err.toString() });
  }
}

function getOrCreateSheet(ss, name, defaultHeaders) {
  let sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
    if (defaultHeaders && defaultHeaders.length > 0) {
      sheet.appendRow(defaultHeaders);
      sheet.getRange(1, 1, 1, defaultHeaders.length).setFontWeight('bold').setBackground('#E2EBD8');
    }
  }
  return sheet;
}

function jsonResponse(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
