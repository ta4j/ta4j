/*
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

import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
        TimeSeries series = new BaseTimeSeries();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Duration weekDuration = Duration.ofDays(7);
        List<Row> rows = readDataAfterHeader(sheet);
        for (Row row : rows) {
            if (row == null) {
                continue;
            }
            // price data exists in first 6 columns (week date, open, high, low, close, volume)
            CellValue[] cellValues = new CellValue[6];
            for (int i = 0; i < 6; i++) {
                cellValues[i] = evaluator.evaluate(row.getCell(i));
            }
            Date weekEndDate = HSSFDateUtil.getJavaDate(cellValues[0].getNumberValue());
            ZonedDateTime weekEndDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(weekEndDate.getTime()), ZoneId.systemDefault());
            Bar bar = new BaseBar(weekDuration, weekEndDateTime,
                    Decimal.valueOf(cellValues[1].formatAsString()),
                    Decimal.valueOf(cellValues[2].formatAsString()),
                    Decimal.valueOf(cellValues[3].formatAsString()),
                    Decimal.valueOf(cellValues[4].formatAsString()),
                    Decimal.valueOf(cellValues[5].formatAsString()));
            series.addBar(bar);
        }
        return series;
    }

    public static List<Decimal> readValues(Sheet sheet, int columnIndex) throws Exception {
        List<Decimal> values = new ArrayList<>();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        List<Row> rows = readDataAfterHeader(sheet);
        for (Row row : rows) {
            Cell cell = row.getCell(columnIndex);
            CellValue value = evaluator.evaluate(cell);
            double d = value.getNumberValue();
            values.add(Decimal.valueOf(d));
        }
        return values;
    }
    
    public static List<Row> readDataAfterHeader(Sheet sheet) {
        // header row contains "Date" in the first cell
        // header row may appear anywhere in the sheet
        // data starts after the header row
        List<Row> rows = new ArrayList<Row>();
        boolean noHeader = true;
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            Cell cell = row.getCell(0);
            CellValue value = evaluator.evaluate(cell);
            if (value.formatAsString().contains("Date")) {
                noHeader = false;
                continue;
            }
            if (noHeader) {
                continue;
            }
            rows.add(row);
        }
        return rows;
            
    }

    public static void testXlsIndicator(Class testClass, String xlsFileName, int valueColumnIdx, IndicatorFactory indicatorFactory, Double... params) throws Exception {
        // read time series from xls
        Sheet sheet = getDataSheet(testClass, xlsFileName);
        TimeSeries inputSeries = readTimeSeries(sheet);
        // compute and read expected values from xls
        for (int i = 0; i < params.length; i++) {
            setParamValue(sheet, i, params[i]);
        }
        List<Decimal> expectedValues = readValues(sheet, valueColumnIdx);
        // create indicator using time series
        Indicator<Decimal> actualIndicator = indicatorFactory.createIndicator(inputSeries);
        // compare values computed by indicator with values computed independently in excel
        TATestsUtils.assertValuesEquals(actualIndicator, expectedValues);
    }
    public static <T> void testXlsIndicator(Class testClass, String xlsFileName, int valueColumnIdx, IndicatorFactory indicatorFactory, T... params) throws Exception {
        Double[] doubleParams = new Double[params.length];
        for (int i = 0; i < params.length; i++) {
            doubleParams[i] = Double.valueOf(params[i].toString());
        }
        testXlsIndicator(testClass, xlsFileName, valueColumnIdx, indicatorFactory, doubleParams);
    }
}
