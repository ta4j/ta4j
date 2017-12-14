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
import org.ta4j.core.mocks.MockTradingRecord;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.zip.DataFormatException;

import static junit.framework.TestCase.assertEquals;

public class XlsTestsUtils {

    public static Sheet getDataSheet(Class clazz, String xlsFileName) throws Exception {
        // we assume that first sheet contains data
        HSSFWorkbook workbook = new HSSFWorkbook(clazz.getResourceAsStream(xlsFileName));
        Sheet sheet = workbook.getSheetAt(0);
        return sheet;
    }

    public static void setParams(Sheet sheet, Decimal... params) throws DataFormatException {
        // the parameters follow a parameters header row with the first cell containing "Param"
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Iterator<Row> iterator = sheet.rowIterator();
        while (iterator.hasNext()) {
            Row row = iterator.next();
            if (evaluator.evaluate(row.getCell(0)).formatAsString().contains("Param")) {
                Arrays.stream(params).mapToDouble(Decimal::doubleValue).forEach(d -> iterator.next().getCell(1).setCellValue(d));
                return;
            }
        }
        throw new DataFormatException("\"Param\" header row not found");
    }

    public static TimeSeries readTimeSeries(Sheet sheet) throws DataFormatException {
        TimeSeries series = new BaseTimeSeries();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Duration weekDuration = Duration.ofDays(7);
        List<Row> rows = readDataAfterHeader(sheet);
        for (Row row : rows) {
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
            String s = evaluator.evaluate(row.getCell(columnIndex)).formatAsString();
            values.add(Decimal.valueOf(s));
        }
        return values;
    }

    public static List<Row> readDataAfterHeader(Sheet sheet) throws DataFormatException {
        // the data follow a data header row with the first cell containing "Date"
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Iterator<Row> iterator = sheet.rowIterator();
        boolean noHeader = true;
        List<Row> rows = new ArrayList<Row>();
        while (iterator.hasNext()) {
            Row row = iterator.next();

            if(row.getCell(0)==null){
                continue; // avoid NPE in line 122
            }
            if (noHeader == false) {
                if (evaluator.evaluate(row.getCell(0)).formatAsString().compareTo("\"//\"") != 0) {
                    rows.add(row);
                }
            }
            if (noHeader && evaluator.evaluate(row.getCell(0)).formatAsString().contains("Date")) {
                noHeader = false;
            }
        }
        if (noHeader) {
            throw new DataFormatException("\"Date\" header row not found");
        }
        return rows;
    }

    public static void testXlsIndicator(Class testClass, String xlsFileName, int valueColumnIdx, IndicatorFactory indicatorFactory, Decimal... params) throws Exception {
        // read time series from xls
        Sheet sheet = getDataSheet(testClass, xlsFileName);
        TimeSeries inputSeries = readTimeSeries(sheet);
        // compute and read expected values from xls
        setParams(sheet, params);
        List<Decimal> expectedValues = readValues(sheet, valueColumnIdx);
        // create indicator using time series
        Indicator<Decimal> actualIndicator = indicatorFactory.createIndicator(inputSeries);
        // compare values computed by indicator with values computed independently in excel
        TATestsUtils.assertValuesEquals(actualIndicator, expectedValues);
    }

    public static <T> void testXlsIndicator(Class testClass, String xlsFileName, int valueColumnIdx, IndicatorFactory indicatorFactory, T... params) throws Exception {

        Decimal[] decimalParams = Arrays.stream(params).map(p -> Decimal.valueOf(p.toString())).toArray(Decimal[]::new);
        testXlsIndicator(testClass, xlsFileName, valueColumnIdx, indicatorFactory, decimalParams);
    }


    public static void testXlsCriterion(Class testClass, String xlsFileName, int stateColumnIdx, int valueColumnIdx, AnalysisCriterion analysisCriterion, Decimal... params) throws Exception {
        // read time series from xls
        Sheet sheet = getDataSheet(testClass, xlsFileName);
        TimeSeries inputSeries = readTimeSeries(sheet);
        // compute and read expected values from xls
        setParams(sheet, params);
        List<Decimal> expectedValues = readValues(sheet, valueColumnIdx);
        Decimal expectedValue = expectedValues.get(expectedValues.size() - 1);
        // create trading record using states
        List<Decimal> states = readValues(sheet, stateColumnIdx);
        TradingRecord tradingRecord = new MockTradingRecord(states);
        // calculate criterion using series and trading record
        double actualValue = analysisCriterion.calculate(inputSeries, tradingRecord);
        // compare value computed by criterion with value computed independently in excel
        assertEquals(actualValue, expectedValue.doubleValue(), TATestsUtils.TA_OFFSET);
    }

    public static <T> void testXlsCriterion(Class testClass, String xlsFileName, int stateColumnIdx, int valueColumnIdx, AnalysisCriterion analysisCriterion, T... params) throws Exception {

        Decimal[] decimalParams = Arrays.stream(params).map(p -> Decimal.valueOf(p.toString())).toArray(Decimal[]::new);


        testXlsCriterion(testClass, xlsFileName, stateColumnIdx, valueColumnIdx, analysisCriterion, decimalParams);
    }
}
