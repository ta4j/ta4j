package ta4jexamples.indicators;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PriceVariationIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import ta4jexamples.loaders.CsvTradesLoader;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class builds a CSV file containing values from indicators.
 */
public class IndicatorsToCsv {

    public static void main(String[] args) {

        /*
          Getting time series
         */
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();

        /*
          Creating indicators
         */
        // Close price
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        // Typical price
        TypicalPriceIndicator typicalPrice = new TypicalPriceIndicator(series);
        // Price variation
        PriceVariationIndicator priceVariation = new PriceVariationIndicator(series);
        // Simple moving averages
        SMAIndicator shortSma = new SMAIndicator(closePrice, 8);
        SMAIndicator longSma = new SMAIndicator(closePrice, 20);
        // Exponential moving averages
        EMAIndicator shortEma = new EMAIndicator(closePrice, 8);
        EMAIndicator longEma = new EMAIndicator(closePrice, 20);
        // Percentage price oscillator
        PPOIndicator ppo = new PPOIndicator(closePrice, 12, 26);
        // Rate of change
        ROCIndicator roc = new ROCIndicator(closePrice, 100);
        // Relative strength index
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        // Williams %R
        WilliamsRIndicator williamsR = new WilliamsRIndicator(series, 20);
        // Average true range
        ATRIndicator atr = new ATRIndicator(series, 20);
        // Standard deviation
        StandardDeviationIndicator sd = new StandardDeviationIndicator(closePrice, 14);

        /*
          Building header
         */
        StringBuilder sb = new StringBuilder("timestamp,close,typical,variation,sma8,sma20,ema8,ema20,ppo,roc,rsi,williamsr,atr,sd\n");

        /*
          Adding indicators values
         */
        final int nbBars = series.getBarCount();
        for (int i = 0; i < nbBars; i++) {
            sb.append(series.getBar(i).getEndTime()).append(',')
            .append(closePrice.getValue(i)).append(',')
            .append(typicalPrice.getValue(i)).append(',')
            .append(priceVariation.getValue(i)).append(',')
            .append(shortSma.getValue(i)).append(',')
            .append(longSma.getValue(i)).append(',')
            .append(shortEma.getValue(i)).append(',')
            .append(longEma.getValue(i)).append(',')
            .append(ppo.getValue(i)).append(',')
            .append(roc.getValue(i)).append(',')
            .append(rsi.getValue(i)).append(',')
            .append(williamsR.getValue(i)).append(',')
            .append(atr.getValue(i)).append(',')
            .append(sd.getValue(i)).append('\n');
        }

        /*
          Writing CSV file
         */
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter("indicators.csv"));
            writer.write(sb.toString());
        } catch (IOException ioe) {
            Logger.getLogger(IndicatorsToCsv.class.getName()).log(Level.SEVERE, "Unable to write CSV file", ioe);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }

    }
}
