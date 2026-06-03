/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources.json;

import org.ta4j.core.BarSeries;
import org.ta4j.core.utils.DeprecationNotifier;

@Deprecated(since = "0.19", forRemoval = true)
public class GsonBarSeries extends LegacyJsonBarSeriesPayload {

    public GsonBarSeries() {
        DeprecationNotifier.warnOnce(GsonBarSeries.class, "ta4jexamples.datasources.json.JsonFileBarSeriesDataSource",
                "0.24.0");
    }

    @Deprecated(since = "0.19", forRemoval = true)
    public static GsonBarSeries from(BarSeries series) {
        DeprecationNotifier.warnOnce(GsonBarSeries.class, "ta4jexamples.datasources.json.JsonFileBarSeriesDataSource",
                "0.24.0");
        GsonBarSeries result = new GsonBarSeries();
        result.copyFrom(LegacyJsonBarSeriesPayload.from(series));
        return result;
    }

    @Deprecated(since = "0.19", forRemoval = true)
    public BarSeries toBarSeries() {
        DeprecationNotifier.warnOnce(GsonBarSeries.class, "ta4jexamples.datasources.json.JsonFileBarSeriesDataSource",
                "0.24.0");
        return super.toBarSeries();
    }
}
