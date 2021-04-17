package org.ta4j.core.reports;

import org.ta4j.core.num.Num;

public class StrategyFitnessReport {
    private Num rewardToRiskRatio;
    private Num expectancy;

    public StrategyFitnessReport(Num rewardToRiskRatio, Num expectancy) {

        this.rewardToRiskRatio = rewardToRiskRatio;
        this.expectancy = expectancy;
    }

    public Num getRewardToRiskRatio() {
        return rewardToRiskRatio;
    }

    public Num getExpectancy() {
        return expectancy;
    }
}
