package ru.lukin.edododo.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import ru.lukin.edododo.model.ActDocument;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class ExcelExportServiceImpl implements ExcelExportService {

    @Override
    public byte[] exportActsToExcel(List<ActDocument> acts) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet("Акты сверки");

            Row header = sheet.createRow(0);
            String[] headers = {
                    "act_number", "legal_entity", "counterparty", "inn", "kpp",
                    "period", "formation_date", "amount", "file_path",
                    "responsible_accountant", "saby_requisites", "status"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            int rowNum = 1;
            for (ActDocument act : acts) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(nvl(act.getActNumber()));
                row.createCell(1).setCellValue(nvl(act.getLegalEntity()));
                row.createCell(2).setCellValue(nvl(act.getCounterparty()));
                row.createCell(3).setCellValue(nvl(act.getInn()));
                row.createCell(4).setCellValue(nvl(act.getKpp()));
                row.createCell(5).setCellValue(nvl(act.getPeriod()));
                row.createCell(6).setCellValue(nvl(act.getFormationDate()));
                row.createCell(7).setCellValue(act.getAmount() == null ? 0.0 : act.getAmount());
                row.createCell(8).setCellValue(nvl(act.getFilePath()));
                row.createCell(9).setCellValue(nvl(act.getResponsibleAccountant()));
                row.createCell(10).setCellValue(nvl(act.getSabyRequisites()));
                row.createCell(11).setCellValue(nvl(act.getStatus()));
            }

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка генерации Excel: " + e.getMessage(), e);
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}