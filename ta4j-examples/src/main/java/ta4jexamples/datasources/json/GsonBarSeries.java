/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.json;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.utils.DeprecationNotifier;

import java.util.LinkedList;
import java.util.List;

@Deprecated(since = "0.19", forRemoval = true)
public class GsonBarSeries {

    private String name;
    private List<GsonBarData> ohlc = new LinkedList<>();

    public GsonBarSeries() {
        DeprecationNotifier.warnOnce(GsonBarSeries.class, "ta4jexamples.datasources.json.JsonFileBarSeriesDataSource",
                "0.24.0");
    }

    @Deprecated(since = "0.19", forRemoval = true)
    public static GsonBarSeries from(BarSeries series) {
        DeprecationNotifier.warnOnce(GsonBarSeries.class, "ta4jexamples.datasources.json.JsonFileBarSeriesDataSource",
                "0.24.0");
        GsonBarSeries result = new GsonBarSeries();
        result.name = series.getName();
        List<Bar> barData = series.getBarData();
        for (Bar bar : barData) {
            GsonBarData exportableBarData = GsonBarData.from(bar);
            result.ohlc.add(exportableBarData);
        }
        return result;
    }

    @Deprecated(since = "0.19", forRemoval = true)
    public BarSeries toBarSeries() {
        DeprecationNotifier.warnOnce(GsonBarSeries.class, "ta4jexamples.datasources.json.JsonFileBarSeriesDataSource",
                "0.24.0");
        BaseBarSeries result = new BaseBarSeriesBuilder().withName(this.name).build();
        for (GsonBarData data : ohlc) {
            data.addTo(result);
        }
        return result;
    }
}
