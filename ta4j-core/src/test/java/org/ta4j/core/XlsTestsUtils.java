/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
 * authors (see AUTHORS)
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
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.zip.DataFormatException;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.mocks.MockTradingRecord;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

public class XlsTestsUtils {

    /**
     * Returns the first Sheet (mutable) from a workbook with the file name in the test class's resources.
     * * 从工作簿返回第一个工作表（可变），文件名在测试类的资源中。
     *
     * @param clazz    class containing the file resources
     *                 包含文件资源的类
     * @param fileName file name of the file containing the workbook
     *                 包含工作簿的文件的文件名
     * @return Sheet number zero from the workbook (mutable)
     *              工作簿中的工作表编号为零（可变）
     * @throws IOException if inputStream returned by getResourceAsStream is null or if HSSFWorkBook constructor throws IOException or if   close throws IOException
     * * @throws IOException 如果 getResourceAsStream 返回的 inputStream 为 null 或者 HSSFWorkBook 构造函数抛出 IOException 或者 close 抛出 IOException
     */
    private static Sheet getSheet(Class<?> clazz, String fileName) throws IOException {
        InputStream inputStream = clazz.getResourceAsStream(fileName);
        if (inputStream == null) {
            throw new IOException("Null InputStream for file 文件的空输入流 " + fileName);
        }
        HSSFWorkbook workbook = new HSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0);
        workbook.close();
        return sheet;
    }

    /**
     * Writes the parameters into the second column of the parameters section of a
      mutable sheet. The parameters section starts after the parameters section
      header. There must be at least params.size() rows between the parameters
      section header and the data section header or part of the data section will
      be overwritten.
     * 将参数写入a的参数部分的第二列
     可变表。 参数部分在参数部分之后开始
     标题。 参数之间必须至少有 params.size() 行
     节头和数据节头或数据节的一部分将
     被覆盖。
     *
     * @param sheet  mutable Sheet
     *               可变工作表
     * @param params parameters to write
     *               写入参数
     * @throws DataFormatException if the parameters section header is not found
     * @throws DataFormatException 如果未找到参数部分标题
     */
    private static void setParams(Sheet sheet, Num... params) throws DataFormatException {
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Iterator<Row> iterator = sheet.rowIterator();
        while (iterator.hasNext()) {
            Row row = iterator.next();
            // skip rows with an empty first cell
            // 跳过第一个单元格为空的行
            if (row.getCell(0) == null) {
                continue;
            }
            // parameters section header is first row with "Param" in first cell
            // 参数部分标题是第一行，第一个单元格中带有“Param”
            if (evaluator.evaluate(row.getCell(0)).formatAsString().contains("Param")) {
                // stream parameters into the second column of subsequent rows
                // 将参数流式传输到后续行的第二列
                // overwrites data section if there is not a large enough gap
                // 如果没有足够大的间隙，则覆盖数据部分
                Arrays.stream(params).mapToDouble(Num::doubleValue)
                        .forEach(d -> iterator.next().getCell(1).setCellValue(d));
                return;
            }
        }
        // the parameters section header was not found
        // 未找到参数部分标题
        throw new DataFormatException("\"Param\" header row not found 找不到标题行");
    }

    /**
     * Gets the BarSeries from a file.
     * * 从文件中获取 BarSeries。
     *
     * @param clazz    class containing the file resources
     *                 包含文件资源的类
     * @param fileName file name of the file resource
     *                 文件资源的文件名
     * @return BarSeries of the data
     *          数据的 BarSeries
     * @throws IOException         if getSheet throws IOException
     *                          如果 getSheet 抛出 IOException
     * @throws DataFormatException if getSeries throws DataFormatException
     * * @throws DataFormatException 如果 getSeries 抛出 DataFormatException
     */
    public static BarSeries getSeries(Class<?> clazz, String fileName, Function<Number, Num> numFunction)
            throws IOException, DataFormatException {
        Sheet sheet = getSheet(clazz, fileName);
        return getSeries(sheet, numFunction);
    }

    /**
     * Gets a BarSeries from the data section of a mutable Sheet. Data follows a
     data section header and appears in the first six columns to the end of the
     file. Empty cells in the data are forbidden.
     从可变工作表的数据部分获取 BarSeries。 数据遵循
     数据节标题并出现在前六列到末尾
     文件。 数据中的空单元格是禁止的。
     *
     * @param sheet mutable Sheet
     *              可变工作表
     *
     * @return BarSeries of the data
     * @return 数据的 BarSeries
     * @throws DataFormatException if getData throws DataFormatException or if the data contains empty cells
     * * @throws DataFormatException 如果 getData 抛出 DataFormatException 或者数据包含空单元格
     */
    private static BarSeries getSeries(Sheet sheet, Function<Number, Num> numFunction) throws DataFormatException {
        BarSeries series = new BaseBarSeriesBuilder().withNumTypeOf(numFunction).build();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        List<Row> rows = getData(sheet);
        int minInterval = Integer.MAX_VALUE;
        int previousNumber = Integer.MAX_VALUE;
        // find the minimum interval in days
        // 以天为单位找到最小间隔
        for (Row row : rows) {
            int currentNumber = (int) evaluator.evaluate(row.getCell(0)).getNumberValue();
            if (previousNumber != Integer.MAX_VALUE) {
                int interval = currentNumber - previousNumber;
                if (interval < minInterval) {
                    minInterval = interval;
                }
            }
            previousNumber = currentNumber;
        }
        Duration duration = Duration.ofDays(minInterval);
        // parse the bars from the data section
        // 从数据部分解析柱形图
        for (Row row : rows) {
            CellValue[] cellValues = new CellValue[6];
            for (int i = 0; i < 6; i++) {
                // empty cells in the data section are forbidden
                // 数据部分的空单元格被禁止
                if (row.getCell(i) == null) {
                    throw new DataFormatException("empty cell in xls bar series data = xls 条形系列数据中的空单元格");
                }
                cellValues[i] = evaluator.evaluate(row.getCell(i));
            }
            // add a bar to the series
            // 为系列添加一个条形图
            Date endDate = DateUtil.getJavaDate(cellValues[0].getNumberValue());
            ZonedDateTime endDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(endDate.getTime()),
                    ZoneId.systemDefault());
            series.addBar(duration, endDateTime,
                    // open, high, low, close, volume
                    // 开盘价、最高价、最低价、收盘价、成交量
                    numFunction.apply(new BigDecimal(cellValues[1].formatAsString())),
                    numFunction.apply(new BigDecimal(cellValues[2].formatAsString())),
                    numFunction.apply(new BigDecimal(cellValues[3].formatAsString())),
                    numFunction.apply(new BigDecimal(cellValues[4].formatAsString())),
                    numFunction.apply(new BigDecimal(cellValues[5].formatAsString())), numFunction.apply(0));
        }
        return series;
    }

    /**
     * Converts Object parameters into Num parameters and calls getValues on a column of a mutable sheet.
     * * 将 Object 参数转换为 Num 参数，并在可变工作表的列上调用 getValues。
     *
     * @param sheet  mutable Sheet
     *               可变工作表
     *
     * @param column column number of the values to get
     *               要获取的值的列号
     *
     * @param params Object parameters to convert to Num
     *               要转换为 Num 的对象参数
     *
     * @return List<Num> of values from the column
     * @return List<Number> 列中的值
     *
     * @throws DataFormatException if getValues returns DataFormatException
     * @throws DataFormatException 如果 getValues 返回 DataFormatException
     */
    private static List<Num> getValues(Sheet sheet, int column, Function<Number, Num> numFunction, Object... params)
            throws DataFormatException {
        Num[] NumParams = Arrays.stream(params).map(p -> numFunction.apply(new BigDecimal(p.toString())))
                .toArray(Num[]::new);
        return getValues(sheet, column, numFunction, NumParams);
    }

    /**
     * Writes the parameters to a mutable Sheet then gets the values from the column.
     * * 将参数写入可变工作表，然后从列中获取值。
     *
     * @param sheet  mutable Sheet
     *               可变工作表
     *
     * @param column column number of the values to get
     *               要获取的值的列号
     * @param params Num parameters to write to the Sheet
     *               要写入工作表的参数数量
     *
     * @return List<Num> of values from the column after the parameters have been  written
     * * @return List<Number> 列中参数写入后的值
     *
     * @throws DataFormatException if setParams or getValues throws  DataFormatException
     * * @throws DataFormatException 如果 setParams 或 getValues 抛出 DataFormatException
     */
    private static List<Num> getValues(Sheet sheet, int column, Function<Number, Num> numFunction, Num... params)
            throws DataFormatException {
        setParams(sheet, params);
        return getValues(sheet, column, numFunction);
    }

    /**
     * Gets the values in a column of the data section of a sheet. Rows with an empty first cell are ignored.
     * * 获取工作表数据部分的列中的值。 第一个单元格为空的行将被忽略。
     *
     * @param sheet  mutable Sheet
     *               可变工作表
     *
     * @param column column number of the values to get
     *               要获取的值的列号
     *
     * @return List<Num> of values from the column
     *  @return List<Number> 列中的值
     *
     * @throws DataFormatException if getData throws DataFormatException
     * * @throws DataFormatException 如果 getData 抛出 DataFormatException
     */
    private static List<Num> getValues(Sheet sheet, int column, Function<Number, Num> numFunction)
            throws DataFormatException {
        List<Num> values = new ArrayList<>();
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        // get all of the data from the data section of the sheet
        // 从工作表的数据部分获取所有数据
        List<Row> rows = getData(sheet);
        for (Row row : rows) {
            // skip rows where the first cell is empty
            // 跳过第一个单元格为空的行
            if (row.getCell(column) == null) {
                continue;
            }
            String s = evaluator.evaluate(row.getCell(column)).formatAsString();
            if (s.equals("#DIV/0!")) {
                values.add(NaN.NaN);
            } else {
                values.add(numFunction.apply(new BigDecimal(s)));
            }
        }
        return values;
    }

    /**
     * Gets all data rows in the data section, following the data section header to the end of the sheet. Skips rows that start with "//" as data comments.
     * * 获取数据部分中的所有数据行，从数据部分标题到工作表末尾。 跳过以“//”开头的行作为数据注释。
     *
     * @param sheet mutable Sheet
     *              可变工作表
     *
     * @return List<Row> of the data rows
     * @return List<Row> 的数据行
     *
     * @throws DataFormatException if the data section header is not found.
     * * @throws DataFormatException 如果未找到数据节标题。
     */
    private static List<Row> getData(Sheet sheet) throws DataFormatException {
        FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
        Iterator<Row> iterator = sheet.rowIterator();
        boolean noHeader = true;
        List<Row> rows = new ArrayList<Row>();
        // iterate through all rows of the sheet
        // 遍历工作表的所有行
        while (iterator.hasNext()) {
            Row row = iterator.next();
            // skip rows with an empty first cell
            // 跳过第一个单元格为空的行
            if (row.getCell(0) == null) {
                continue;
            }
            // after the data section header is found, add all rows that don't have "//" in the first cell
            // 找到数据节标题后，在第一个单元格中添加所有没有“//”的行
            if (!noHeader) {
                if (evaluator.evaluate(row.getCell(0)).formatAsString().compareTo("\"//\"") != 0) {
                    rows.add(row);
                }
            }
            // if the data section header is not found and this row has "Date" in its first cell, then mark the header as found
            // 如果未找到数据节标题并且该行在其第一个单元格中具有“日期”，则将标题标记为已找到
            if (noHeader && evaluator.evaluate(row.getCell(0)).formatAsString().contains("Date")) {
                noHeader = false;
            }
        }
        // if the header was not found throw an exception
        // 如果没有找到头文件，则抛出异常
        if (noHeader) {
            throw new DataFormatException("\"Date\" header row not found 找不到标题行");
        }
        return rows;
    }

    /**
     * Gets an Indicator from a column of an XLS file parameters.
     * * 从 XLS 文件参数的列中获取指标。
     *
     * @param clazz    class containing the file resource
     *                 包含文件资源的类
     *
     * @param fileName file name of the file resource
     *                 文件资源的文件名
     *
     * @param column   column number of the indicator values
     *                 指标值的列号
     *
     * @param params   indicator parameters
     *                 指标参数
     *
     * @return Indicator<Num> as calculated by the XLS file given the parameters
     * * @return Indicator<Num> 由给定参数的 XLS 文件计算得出
     *
     * @throws IOException         if getSheet throws IOException
     * * @throws IOException 如果 getSheet 抛出 IOException
     *
     * @throws DataFormatException if getSeries or getValues throws  DataFormatException
     * * @throws DataFormatException 如果 getSeries 或 getValues 抛出 DataFormatException
     */
    public static Indicator<Num> getIndicator(Class<?> clazz, String fileName, int column,
            Function<Number, Num> numFunction, Object... params) throws IOException, DataFormatException {
        Sheet sheet = getSheet(clazz, fileName);
        return new MockIndicator(getSeries(sheet, numFunction), getValues(sheet, column, numFunction, params));
    }

    /**
     * Gets the final criterion value from a column of an XLS file given parameters.
     * * 从给定参数的 XLS 文件的列中获取最终标准值。
     *
     * @param clazz    test class containing the file resources
     *                 包含文件资源的测试类
     *
     * @param fileName file name of the file resource
     *                 文件资源的文件名
     *
     * @param column   column number of the calculated criterion values
     *                 计算的标准值的列号
     *
     * @param params   criterion parameters
     *                 标准参数
     *
     * @return Num final criterion value as calculated by the XLS file given the   parameters
     * * @return Num 最终标准值，由给定参数的 XLS 文件计算得出
     *
     * @throws IOException         if getSheet throws IOException
     *                          如果 getSheet 抛出 IOException
     *
     * @throws DataFormatException if getValues throws DataFormatException
     *                      如果 getValues 抛出 DataFormatException
     */
    public static Num getFinalCriterionValue(Class<?> clazz, String fileName, int column,
            Function<Number, Num> numFunction, Object... params) throws IOException, DataFormatException {
        Sheet sheet = getSheet(clazz, fileName);
        List<Num> values = getValues(sheet, column, numFunction, params);
        return values.get(values.size() - 1);
    }

    /**
     * Gets the trading record from an XLS file.
     * * 从 XLS 文件中获取交易记录。
     *
     * @param clazz    the test class containing the file resources
     *                 包含文件资源的测试类
     *
     * @param fileName file name of the file resource
     *                 文件资源的文件名
     *
     * @param column   column number of the trading record
     *                 交易记录栏号
     *
     * @return TradingRecord from the file
     * 从文件中@return TradingRecord
     *
     * @throws IOException         if getSheet throws IOException
     *                              如果 getSheet 抛出 IOException
     *
     * @throws DataFormatException if getValues throws DataFormatException
     *                              如果 getValues 抛出 DataFormatException
     */
    public static TradingRecord getTradingRecord(Class<?> clazz, String fileName, int column,
            Function<Number, Num> numFunction) throws IOException, DataFormatException {
        Sheet sheet = getSheet(clazz, fileName);
        return new MockTradingRecord(getValues(sheet, column, numFunction));
    }

}