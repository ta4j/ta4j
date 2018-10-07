package ta4jexamples.loaders;

import com.opencsv.CSVReader;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.TimeSeries;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class build a Ta4j time series from a CSV file containing bars.
 */
public class CsvBarsLoader {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * @return a time series from Apple Inc. bars.
     */
    public static TimeSeries loadAppleIncSeries() {
        return loadCsvSeries("appleinc_bars_from_20130101_usd.csv");
    }

    public static TimeSeries loadCsvSeries(String filename) {

        InputStream stream = CsvBarsLoader.class.getClassLoader().getResourceAsStream(filename);

        TimeSeries series =  new BaseTimeSeries("apple_bars");

        try (CSVReader csvReader = new CSVReader(new InputStreamReader(stream, Charset.forName("UTF-8")), ',', '"', 1)) {
            String[] line;
            while ((line = csvReader.readNext()) != null) {
                ZonedDateTime date = LocalDate.parse(line[0], DATE_FORMAT).atStartOfDay(ZoneId.systemDefault());
                double open = Double.parseDouble(line[1]);
                double high = Double.parseDouble(line[2]);
                double low = Double.parseDouble(line[3]);
                double close = Double.parseDouble(line[4]);
                double volume = Double.parseDouble(line[5]);

                series.addBar(date, open, high, low, close, volume);
            }
        } catch (IOException ioe) {
            Logger.getLogger(CsvBarsLoader.class.getName()).log(Level.SEVERE, "Unable to load bars from CSV", ioe);
        } catch (NumberFormatException nfe) {
            Logger.getLogger(CsvBarsLoader.class.getName()).log(Level.SEVERE, "Error while parsing value", nfe);
        }
        return series;
    }

    public static void main(String[] args) {
        TimeSeries series = CsvBarsLoader.loadAppleIncSeries();

        System.out.println("Series: " + series.getName() + " (" + series.getSeriesPeriodDescription() + ")");
        System.out.println("Number of bars: " + series.getBarCount());
        System.out.println("First bar: \n"
                + "\tVolume: " + series.getBar(0).getVolume() + "\n"
                + "\tOpen price: " + series.getBar(0).getOpenPrice()+ "\n"
                + "\tClose price: " + series.getBar(0).getClosePrice());
    }
}
