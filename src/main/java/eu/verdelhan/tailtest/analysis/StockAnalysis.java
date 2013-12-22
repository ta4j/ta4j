package net.sf.tail.analysis;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.StrategiesSet;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.analysis.evaluator.StrategyEvaluatorFactory;
import net.sf.tail.analysis.walk.WalkForward;
import net.sf.tail.report.Report;
import net.sf.tail.runner.RunnerFactory;
import net.sf.tail.series.SerializableTimeSeries;

public class StockAnalysis implements Serializable {

	private static final long serialVersionUID = 8039932898223937322L;

	private TimeSeriesSlicer slicer;

	private AnalysisCriterion applyedCriterion;

	private List<AnalysisCriterion> additionalCriteria;

	private SerializableTimeSeries stock;

	private List<Report> reports;

	private WalkForward walker;

	private StrategyEvaluatorFactory evaluatorFactory;

	private RunnerFactory runnerFactory;

	public StockAnalysis(SerializableTimeSeries stock, AnalysisCriterion applyedCriterion, TimeSeriesSlicer slicer, StrategyEvaluatorFactory evaluatorFactory, RunnerFactory runnerFactory) {
		this.stock = stock;
		this.applyedCriterion = applyedCriterion;
		this.slicer = slicer;
		this.evaluatorFactory = evaluatorFactory;
		this.runnerFactory = runnerFactory;

		walker = new WalkForward(evaluatorFactory,runnerFactory);
		reports = new ArrayList<Report>();
		additionalCriteria = new ArrayList<AnalysisCriterion>();

	}

	public Report addReport(String reportName, StrategiesSet strategiesSet) {
		Report report = createReport(reportName, strategiesSet);

		reports.add(report);
		return report;
	}

	private Report createReport(String reportName, StrategiesSet strategiesSet) {

		Report report = walker.walk(strategiesSet, slicer, applyedCriterion);
		report.setName(reportName);

		for (AnalysisCriterion criterion : additionalCriteria) {
			report.addSummarizedCriteria(criterion);
		}
		return report;
	}

	public void addCriterion(AnalysisCriterion criterion) {
		for (Report report : reports) {
			report.addSummarizedCriteria(criterion);
		}
		additionalCriteria.add(criterion);
	}

	public void addCriteria(List<AnalysisCriterion> criteria) {
		for (AnalysisCriterion criterion : criteria) {
			addCriterion(criterion);
		}
	}

	public TimeSeriesSlicer getSlicer() {
		return slicer;
	}

	public AnalysisCriterion getApplyedCriterion() {
		return applyedCriterion;
	}

	public SerializableTimeSeries getStock() {
		return stock;
	}

	public StrategyEvaluatorFactory getEvaluatorFactory() {
		return evaluatorFactory;
	}

	public List<Report> getReports() {
		return reports;
	}

	public void reloadReports() {
		List<Report> newReports = new ArrayList<Report>();
		for (int i = 0; i < reports.size(); i++) {
			Report newReport = createReport(reports.get(i).getName(), reports.get(i).getTechnic().getStrategiesSet());
			newReports.add(newReport);
		}
		System.out.println("=-----------------------------------------------------------------");
		reports = newReports;
	}

	public WalkForward getWalker() {
		return walker;
	}

	public List<AnalysisCriterion> getAdditionalCriteria() {
		return additionalCriteria;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((applyedCriterion == null) ? 0 : applyedCriterion.hashCode());
		result = prime * result + ((evaluatorFactory == null) ? 0 : evaluatorFactory.hashCode());
		result = prime * result + (reports.hashCode());
		result = prime * result + ((slicer == null) ? 0 : slicer.hashCode());
		result = prime * result + ((stock == null) ? 0 : stock.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final StockAnalysis other = (StockAnalysis) obj;
		if (applyedCriterion == null) {
			if (other.applyedCriterion != null)
				return false;
		} else if (!applyedCriterion.equals(other.applyedCriterion))
			return false;
		if (evaluatorFactory == null) {
			if (other.evaluatorFactory != null)
				return false;
		} else if (!evaluatorFactory.equals(other.evaluatorFactory))
			return false;
		if (!reports.equals(other.reports))
			return false;
		if (slicer == null) {
			if (other.slicer != null)
				return false;
		} else if (!slicer.equals(other.slicer))
			return false;
		if (stock == null) {
			if (other.stock != null)
				return false;
		} else if (!stock.equals(other.stock))
			return false;
		return true;
	}

	public RunnerFactory getRunnerFactory() {
		return runnerFactory;
	}	

}
