package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.analysis.criteria.AbstractAnalysisCriterion;
import eu.verdelhan.ta4j.analysis.criteria.AverageProfitCriterion;
import eu.verdelhan.ta4j.analysis.criteria.BuyAndHoldCriterion;
import eu.verdelhan.ta4j.analysis.criteria.RewardRiskRatioCriterion;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class AbstractAnalysisCriterionTest {

	@Test
	public void testToString() {
		AbstractAnalysisCriterion c1 = new AverageProfitCriterion();
		assertEquals("Average Profit", c1.toString());
		AbstractAnalysisCriterion c2 = new BuyAndHoldCriterion();
		assertEquals("Buy And Hold", c2.toString());
		AbstractAnalysisCriterion c3 = new RewardRiskRatioCriterion();
		assertEquals("Reward Risk Ratio", c3.toString());
	}

}
