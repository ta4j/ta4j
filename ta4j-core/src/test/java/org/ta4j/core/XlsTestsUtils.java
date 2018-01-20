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

import java.io.IOException;
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

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.mocks.MockTradingRecord;

public class XlsTestsUtils {

    /**
     * Returns the first Sheet (mutable) from a workbook with the file name in
     * the test class's resources.
     * 
     * @param clazz class containing the file resources
     * @param fileName file name of the file containing the workbook
     * @return Sheet number zero from the workbook (mutable)
     * @throws IOException if the workbook constructor or close throws
     *             IOException
     */
    private static Sheet getSheet(Class<?> clazz, String fileName) throws IOException {
        HSSFWorkbook workbook = new HSSFWorkbook(clazz.getResourceAsStream(fileName));
        Sheet sheet = workbook.getSheetAt(0);
        workbook.close();
        return sheet;
    }

    /**
     * Writes the parameters into the second column of the parameters section of
     * a mutable sheet. The parameters section starts after the parameters
     * section header. There must be at least params.size() rows between the
     * parameters section header and the data section header or part of the data
     * section will be overwritten.
     * 
     * @param sheet mutable Sheet
     * @param params parameters to write
     * @throws DataFormatException if the parameters section header is not found
     */
    private static void setParams(Sheet sheet, Decimal... params) throws DataFormatException {
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Iterator<Row> iterator = sheet.rowIterator();
        while (iterator.hasNext()) {
            Row row = iterator.next();
            // skip rows with an empty first cell
            if (row.getCell(0) == null) {
                continue;
            }
            // parameters section header is first row with "Param" in first cell
            if (evaluator.evaluate(row.getCell(0)).formatAsString().contains("Param")) {
                // stream parameters into the second column of subsequent rows
                // overwrites data section if there is not a large enough gap
                Arrays.stream(params)
                .mapToDouble(Decimal::doubleValue)
                .forEach(d -> iterator.next().getCell(1).setCellValue(d));
                return;
            }
        }
        // the parameters section header was not found
        throw new DataFormatException("\"Param\" header row not found");
    }

    /**
     * Gets the TimeSeries from a file.
     * 
     * @param clazz class containing the file resources
     * @param fileName file name of the file resource
     * @return TimeSeries of the data
     * @throws IOException if getSheet throws IOException
     * @throws DataFormatException if getSeries throws DataFormatException
     */
    public static TimeSeries getSeries(Class<?> clazz, String fileName) throws IOException, DataFormatException {
        Sheet sheet = getSheet(clazz, fileName);
        return getSeries(sheet);
    }

    /**
     * Gets a TimeSeries from the data section of a mutable Sheet. Data follows
     * a data section header and appears in the first six columns to the end of
     * the file. Empty cells in the data are forbidden.
     * 
     * @param sheet mutable Sheet
     * @return TimeSeries of the data
     * @throws DataFormatException if getData throws DataFormatException or if
     *             the data contains empty cells
     */
    private static TimeSeries getSeries(Sheet sheet) throws DataFormatException {
        TimeSeries series = new BaseTimeSeries();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Duration weekDuration = Duration.ofDays(7);
        List<Row> rows = getData(sheet);
        // parse the rows from the data section
        for (Row row : rows) {
            CellValue[] cellValues = new CellValue[6];
            for (int i = 0; i < 6; i++) {
                // empty cells in the data section are forbidden
                if (row.getCell(i) == null) {
                    throw new DataFormatException("empty cell in xls time series data");
                }
                cellValues[i] = evaluator.evaluate(row.getCell(i));
            }
            // build a bar from the row and add it to the series
            Date weekEndDate = DateUtil.getJavaDate(cellValues[0].getNumberValue());
            ZonedDateTime weekEndDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(weekEndDate.getTime()), ZoneId.systemDefault());
            Bar bar = new BaseBar(weekDuration, weekEndDateTime,
                    // open, high, low, close, volume
                    Decimal.valueOf(cellValues[1].formatAsString()),
                    Decimal.valueOf(cellValues[2].formatAsString()),
                    Decimal.valueOf(cellValues[3].formatAsString()),
                    Decimal.valueOf(cellValues[4].formatAsString()),
                    Decimal.valueOf(cellValues[5].formatAsString()));
            series.addBar(bar);
        }
        return series;
    }

    /**
     * Converts Object parameters into Decimal parameters and calls getValues on
     * a column of a mutable sheet.
     * 
     * @param sheet mutable Sheet
     * @param column column number of the values to get
     * @param params Object parameters to convert to Decimal
     * @return List<Decimal> of values from the column
     * @throws DataFormatException if getValues returns DataFormatException
     */
    private static List<Decimal> getValues(Sheet sheet, int column, Object... params) throws DataFormatException {
        Decimal[] decimalParams = Arrays.stream(params)
                .map(p -> Decimal.valueOf(p.toString()))
                .toArray(Decimal[]::new);
        return getValues(sheet, column, decimalParams);
    }

    /**
     * Writes the parameters to a mutable Sheet then gets the values from the
     * column.
     * 
     * @param sheet mutable Sheet
     * @param column column number of the values to get
     * @param params Decimal parameters to write to the Sheet
     * @return List<Decimal> of values from the column after the parameters have
     *         been written
     * @throws DataFormatException if setParams or getValues throws
     *             DataFormatException
     */
    private static List<Decimal> getValues(Sheet sheet, int column, Decimal... params) throws DataFormatException {
        setParams(sheet, params);
        return getValues(sheet, column);
    }

    /**
     * Gets the values in a column of the data section of a sheet. Rows with an
     * empty first cell are ignored.
     * 
     * @param sheet mutable Sheet
     * @param column column number of the values to get
     * @return List<Decimal> of values from the column
     * @throws DataFormatException if getData throws DataFormatException
     */
    private static List<Decimal> getValues(Sheet sheet, int column) throws DataFormatException {
        List<Decimal> values = new ArrayList<>();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        // get all of the data from the data section of the sheet
        List<Row> rows = getData(sheet);
        for (Row row : rows) {
            // skip rows where the first cell is empty
            if (row.getCell(column) == null) {
                continue;
            }
            String s = evaluator.evaluate(row.getCell(column)).formatAsString();
            values.add(Decimal.valueOf(s));
        }
        return values;
    }

    /**
     * Gets all data rows in the data section, following the data section header
     * to the end of the sheet. Skips rows that start with "//" as data
     * comments.
     * 
     * @param sheet mutable Sheet
     * @return List<Row> of the data rows
     * @throws DataFormatException if the data section header is not found.
     */
    private static List<Row> getData(Sheet sheet) throws DataFormatException {
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Iterator<Row> iterator = sheet.rowIterator();
        boolean noHeader = true;
        List<Row> rows = new ArrayList<Row>();
        // iterate through all rows of the sheet
        while (iterator.hasNext()) {
            Row row = iterator.next();
            // skip rows with an empty first cell
            if (row.getCell(0) == null) {
                continue;
            }
            // after the data section header is found, add all rows that don't
            // have "//" in the first cell
            if (noHeader == false) {
                if (evaluator.evaluate(row.getCell(0)).formatAsString().compareTo("\"//\"") != 0) {
                    rows.add(row);
                }
            }
            // if the data section header is not found and this row has "Date"
            // in its first cell, then mark the header as found
            if (noHeader && evaluator.evaluate(row.getCell(0)).formatAsString().contains("Date")) {
                noHeader = false;
            }
        }
        // if the header was not found throw an exception
        if (noHeader) {
            throw new DataFormatException("\"Date\" header row not found");
        }
        return rows;
    }

    /**
     * Gets an Indicator from a column of an XLS file parameters.
     * 
     * @param clazz class containing the file resource
     * @param fileName file name of the file resource
     * @param column column number of the indicator values
     * @param params indicator parameters
     * @return Indicator<Decimal> as calculated by the XLS file given the
     *         parameters
     * @throws IOException if getSheet throws IOException
     * @throws DataFormatException if getSeries or getValues throws
     *             DataFormatException
     */
    public static Indicator<Decimal> getIndicator(Class<?> clazz, String fileName, int column, Object... params) throws IOException, DataFormatException {
        Sheet sheet = getSheet(clazz, fileName);
        return new MockIndicator(getSeries(sheet), getValues(sheet, column, params));
    }

    /**
     * Gets the final criterion value from a column of an XLS file given
     * parameters.
     * 
     * @param clazz test class containing the file resources
     * @param fileName file name of the file resource
     * @param column column number of the calculated criterion values
     * @param params criterion parameters
     * @return Decimal final criterion value as calculated by the XLS file given
     *         the parameters
     * @throws IOException if getSheet throws IOException
     * @throws DataFormatException if getValues throws DataFormatException
     */
    public static Decimal getFinalCriterionValue(Class<?> clazz, String fileName, int column, Object... params) throws IOException, DataFormatException {
        Sheet sheet = getSheet(clazz, fileName);
        List<Decimal> values = getValues(sheet, column, params);
        return values.get(values.size() - 1);
    }

    /**
     * Gets the trading record from an XLS file.
     * 
     * @param clazz the test class containing the file resources
     * @param fileName file name of the file resource
     * @param column column number of the trading record
     * @return TradingRecord from the file
     * @throws IOException if getSheet throws IOException
     * @throws DataFormatException if getValues throws DataFormatException
     */
    public static TradingRecord getTradingRecord(Class<?> clazz, String fileName, int column) throws IOException, DataFormatException {
        Sheet sheet = getSheet(clazz, fileName);
        return new MockTradingRecord(getValues(sheet, column));
    }

}