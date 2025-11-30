package com.example.apfeed;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ExcelExporter {
    private static final String[] HEADERS = {
            "id","title","description","link","condition","price","availability",
            "adult","image link","mpn","brand","product types"
    };

    public void export(List<Product> products, String filename, boolean append) throws Exception {
        // Simple implementation: always create a fresh workbook
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Sheet1");

        // Header row
        Row header = sheet.createRow(0);
        CellStyle headerStyle = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        for (int i = 0; i < HEADERS.length; i++) {
            Cell c = header.createCell(i);
            c.setCellValue(HEADERS[i]);
            c.setCellStyle(headerStyle);
        }

        // Data rows
        int rowIdx = 1;
        for (Product p : products) {
            Row r = sheet.createRow(rowIdx++);
            int col = 0;
            r.createCell(col++).setCellValue(nz(p.id));
            r.createCell(col++).setCellValue(nz(p.title));
            r.createCell(col++).setCellValue(nz(p.description));
            r.createCell(col++).setCellValue(nz(p.link));
            r.createCell(col++).setCellValue(nz(p.condition));
            r.createCell(col++).setCellValue(nz(p.price));
            r.createCell(col++).setCellValue(nz(p.availability));
            r.createCell(col++).setCellValue(nz(p.adult));
            r.createCell(col++).setCellValue(nz(p.imageLink));
            r.createCell(col++).setCellValue(nz(p.mpn));
            r.createCell(col++).setCellValue(nz(p.brand));
            r.createCell(col++).setCellValue(nz(p.productTypes));
        }

        for (int i = 0; i < HEADERS.length; i++) {
            sheet.autoSizeColumn(i);
        }

        try (OutputStream out = Files.newOutputStream(Path.of(filename))) {
            wb.write(out);
        }
        wb.close();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}
