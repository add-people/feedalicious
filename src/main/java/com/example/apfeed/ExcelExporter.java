package com.example.apfeed;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ExcelExporter {
    private static final String[] HEADERS = {
            "id","title","description","link","condition","price","availability",
            "adult","image link","mpn","brand","product types"
    };

    public void export(List<Product> products, String filename, boolean append) throws IOException {
        Workbook wb;
        Sheet sheet;
        File file = new File(filename);

        if (append && file.exists()) {
            try (InputStream in = Files.newInputStream(file.toPath())) {
                wb = WorkbookFactory.create(in);
            }
            sheet = wb.getSheetAt(0);
        } else {
            wb = new XSSFWorkbook();
            sheet = wb.createSheet("Feed");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
        }

        int startRow = sheet.getLastRowNum() + 1;
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            Row r = sheet.createRow(startRow + i);
            int c = 0;
            r.createCell(c++).setCellValue(p.id);
            r.createCell(c++).setCellValue(nz(p.title));
            r.createCell(c++).setCellValue(nz(p.description));
            r.createCell(c++).setCellValue(nz(p.link));
            r.createCell(c++).setCellValue(nz(p.condition));
            r.createCell(c++).setCellValue(nz(p.price));
            r.createCell(c++).setCellValue(nz(p.availability));
            r.createCell(c++).setCellValue(nz(p.adult));
            r.createCell(c++).setCellValue(nz(p.imageLink));
            r.createCell(c++).setCellValue(nz(p.mpn));
            r.createCell(c++).setCellValue(nz(p.brand));
            r.createCell(c++).setCellValue(nz(p.productTypes));
        }

        // Auto-size columns (rough equivalent of your width logic)
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

