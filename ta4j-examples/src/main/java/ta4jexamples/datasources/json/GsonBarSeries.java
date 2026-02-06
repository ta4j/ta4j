/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.json;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.util.LinkedList;
import java.util.List;

@Deprecated(since = "0.19")
public class GsonBarSeries {

    private String name;
    private List<GsonBarData> ohlc = new LinkedList<>();

    @Deprecated
    public static GsonBarSeries from(BarSeries series) {
        GsonBarSeries result = new GsonBarSeries();
        result.name = series.getName();
        List<Bar> barData = series.getBarData();
        for (Bar bar : barData) {
            GsonBarData exportableBarData = GsonBarData.from(bar);
            result.ohlc.add(exportableBarData);
        }
        return result;
    }

    @Deprecated
    public BarSeries toBarSeries() {
        BaseBarSeries result = new BaseBarSeriesBuilder().withName(this.name).build();
        for (GsonBarData data : ohlc) {
            data.addTo(result);
        }
        return result;
    }
}
