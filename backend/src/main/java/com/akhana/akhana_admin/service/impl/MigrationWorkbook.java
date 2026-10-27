package com.akhana.akhana_admin.service.impl;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * FIN-11: reads the sheets of the two spreadsheets (Archivo → Descargar → Microsoft Excel). Sheets and columns are
 * found by name, without case, accents or repeated spaces, as in CAT-06; each row keeps its cells by column.
 */
final class MigrationWorkbook {

    static final int MAX_BYTES = 10 * 1024 * 1024;
    static final int MAX_ROWS = 20_000;
    static final String INVALID_FILE = "Los archivos deben ser .xlsx de hasta 10 MB y 20.000 filas por hoja.";

    private static final DateTimeFormatter TEXT_DATE = DateTimeFormatter.ofPattern("d/M/uuuu");

    /** A column of a sheet: its title (and other accepted titles) and whether the sheet needs it. */
    record Column(String key, boolean required, String... titles) {}

    /** A whole-file error: nothing is reviewed nor imported. */
    static final class FileRejected extends IllegalArgumentException {
        FileRejected(String message) {
            super(message);
        }
    }

    /** A data row: the spreadsheet row number (the title is row 1) and its cells by column key. */
    record SheetRow(int rowNumber, Map<String, Cell> cells) {
        Cell cell(String key) {
            return cells.get(key);
        }

        String text(String key) {
            return MigrationWorkbook.text(cells.get(key));
        }

        boolean blank(String key) {
            return text(key).isEmpty();
        }
    }

    private final List<Workbook> workbooks = new ArrayList<>();

    MigrationWorkbook(List<String> fileNames, List<byte[]> contents) {
        for (int i = 0; i < contents.size(); i++) {
            String name = fileNames.get(i);
            byte[] content = contents.get(i);
            if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || content == null || content.length == 0 || content.length > MAX_BYTES) {
                throw new FileRejected(INVALID_FILE);
            }
            try {
                workbooks.add(new XSSFWorkbook(new ByteArrayInputStream(content)));
            } catch (Exception e) {
                // POI rejects a file that is not an .xlsx with its own exceptions
                throw new FileRejected(INVALID_FILE);
            }
        }
    }

    /** The first sheet with that name in any of the files, or null. */
    Sheet sheet(String name) {
        for (Workbook workbook : workbooks) {
            for (Sheet sheet : workbook) {
                if (normalize(sheet.getSheetName()).equals(normalize(name))) {
                    return sheet;
                }
            }
        }
        return null;
    }

    /**
     * The column index of each key in the title row. A title may repeat (Detalle Pedidos has two Codigo): each
     * repetition goes to the next column that lists it. Missing required columns are added to {@code missing}.
     */
    static Map<String, Integer> columns(Sheet sheet, List<Column> columns, List<String> missing) {
        Map<String, Integer> found = new HashMap<>();
        Row header = sheet.getRow(0);
        if (header != null) {
            for (Cell cell : header) {
                String title = normalize(text(cell));
                if (title.isEmpty()) {
                    continue;
                }
                for (Column column : columns) {
                    if (!found.containsKey(column.key())
                        && Arrays.stream(column.titles()).anyMatch(t -> normalize(t).equals(title))) {
                        found.put(column.key(), cell.getColumnIndex());
                        break;
                    }
                }
            }
        }
        for (Column column : columns) {
            if (column.required() && !found.containsKey(column.key())) {
                missing.add(sheet.getSheetName() + " › " + column.titles()[0]);
            }
        }
        return found;
    }

    /** Rows with at least one of the {@code anyOf} cells filled, in order. */
    static List<SheetRow> rows(Sheet sheet, Map<String, Integer> columns, List<String> anyOf) {
        if (sheet.getLastRowNum() > MAX_ROWS) {
            throw new FileRejected(INVALID_FILE);
        }
        List<SheetRow> rows = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Map<String, Cell> cells = new HashMap<>();
            columns.forEach((key, index) -> cells.put(key, row.getCell(index)));
            SheetRow sheetRow = new SheetRow(i + 1, cells);
            if (anyOf.stream().anyMatch(key -> !sheetRow.blank(key))) {
                rows.add(sheetRow);
            }
        }
        return rows;
    }

    // --- Values ---

    /** The real value of the cell (not the one its format shows), as text. */
    static String text(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> clean(cell.getStringCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                ? cell.getLocalDateTimeCellValue().toLocalDate().format(TEXT_DATE)
                : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /** A date cell or a d/m/yyyy text; null (with the error) otherwise. Empty is null without error. */
    static LocalDate date(Cell cell, String title, List<String> errors) {
        String value = text(cell);
        if (value.isEmpty()) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        try {
            return LocalDate.parse(value, TEXT_DATE);
        } catch (DateTimeParseException e) {
            errors.add(title + " no es una fecha: '" + value + "'.");
            return null;
        }
    }

    /** A number from the cell, or text with a decimal point or comma; null (with the error) otherwise. */
    static BigDecimal number(Cell cell, String title, List<String> errors) {
        String value = text(cell);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            errors.add(title + " no es un número: '" + value + "'.");
            return null;
        }
    }

    static String clean(String value) {
        return value == null ? "" : value.replace(' ', ' ').strip();
    }

    /** Without case, accents or repeated spaces: for titles, names and keys. */
    static String normalize(String value) {
        return Normalizer.normalize(clean(value), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
