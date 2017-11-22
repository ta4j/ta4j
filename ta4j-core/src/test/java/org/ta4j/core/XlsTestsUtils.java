/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

public class XlsTestsUtils {

    public static Sheet getDataSheet(Class clazz, String xlsFileName) throws Exception {
        // we assume that first sheet contains data
        HSSFWorkbook workbook = new HSSFWorkbook(clazz.getResourceAsStream(xlsFileName));
        Sheet sheet = workbook.getSheetAt(0);
        return sheet;
    }

    public static void setParamValue(Sheet sheet, int paramIndex, double value) {
        // first param is in B3 cell
        // second param is in B4 cell
        Row row = sheet.getRow(2 + paramIndex);
        Cell cell = row.getCell(1);
        cell.setCellValue(value);
    }

    public static TimeSeries readTimeSeries(Sheet sheet) {
        // prices starts from 7th row until the end of sheet
        // price data exists in first 6 columns (week date, open, high, low, close, volume)
        TimeSeries series = new BaseTimeSeries();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Cell cell;
        CellValue value;
        for (Row row = sheet.getRow(6); row != null; row = sheet.getRow(row.getRowNum() + 1)) {
            cell = row.getCell(0);
            value = evaluator.evaluate(cell);
            Duration weekDuration = Duration.ofDays(7);
            Date weekEndDate = HSSFDateUtil.getJavaDate(value.getNumberValue());
            ZonedDateTime weekEndDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(weekEndDate.getTime()), ZoneId.systemDefault());
            cell = row.getCell(1);
            value = evaluator.evaluate(cell);
            double open = value.getNumberValue();
            cell = row.getCell(2);
            value = evaluator.evaluate(cell);
            double high = value.getNumberValue();
            cell = row.getCell(3);
            value = evaluator.evaluate(cell);
            double low = value.getNumberValue();
            cell = row.getCell(4);
            value = evaluator.evaluate(cell);
            double close = value.getNumberValue();
            cell = row.getCell(5);
            value = evaluator.evaluate(cell);
            double volume = value.getNumberValue();
            Bar bar = new BaseBar(weekDuration, weekEndDateTime,
                    Decimal.valueOf(open),
                    Decimal.valueOf(high),
                    Decimal.valueOf(low),
                    Decimal.valueOf(close),
                    Decimal.valueOf(volume));
            series.addBar(bar);
        }
        return series;
    }

    public static List<Decimal> readValues(Sheet sheet, int columnIndex) throws Exception {
        // values starts from 7th row until the end of sheet (in specified column)
        List<Decimal> values = new ArrayList<>();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Cell cell;
        CellValue value;
        for (Row row = sheet.getRow(6); row != null; row = sheet.getRow(row.getRowNum() + 1)) {
            cell = row.getCell(columnIndex);
            value = evaluator.evaluate(cell);
            double d = value.getNumberValue();
            values.add(Decimal.valueOf(d));
        }
        return values;
    }
}
