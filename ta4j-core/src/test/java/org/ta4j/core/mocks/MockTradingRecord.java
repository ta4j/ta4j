/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.mocks;

import java.util.List;

import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.num.Num;

public class MockTradingRecord extends BaseTradingRecord {

    private static final long serialVersionUID = 6220278197931451635L;

    /*
     * Constructor. Builds a TradingRecord from a list of states. Initial state
     * value is zero. Then at each index where the state value changes, the
     * TradingRecord operates at that index.
     *
     * @param states List<Num> of state values
     */
    public MockTradingRecord(List<Num> states) {
        double lastState = 0d;
        for (int i = 0; i < states.size(); i++) {
            double state = states.get(i).doubleValue();
            if (state != lastState) {
                this.operate(i);
            }
            lastState = state;
        }
    }
}
