/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.forecast;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URL;

import org.junit.jupiter.api.Test;

class KinematicKalmanForecastExampleTest {

    @Test
    void ossifiedSp500FixtureIsAvailable() {
        URL resource = KinematicKalmanForecastExampleTest.class.getClassLoader()
                .getResource(KinematicKalmanForecastExample.SP500_RESOURCE);

        assertNotNull(resource, "S&P 500 resource should be available");
    }
}
