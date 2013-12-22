package net.sf.tail.io;

import java.io.IOException;

import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.analysis.StockAnalysis;
import net.sf.tail.series.SerializableTimeSeries;

import com.thoughtworks.xstream.XStream;

public class StockAnalysisSerializer {

	public String toXML(StockAnalysis analysis) {
		XStream xstream = new XStream();
		return xstream.toXML(analysis);
	}

	@SuppressWarnings("unchecked")
	public StockAnalysis fromXML(String xml) throws IOException {
		XStream xstream = new XStream();
		StockAnalysis analysis = (StockAnalysis) xstream.fromXML(xml);
		SerializableTimeSeries newStock = analysis.getStock();
		newStock.reloadSeries();
		
		TimeSeriesSlicer newSlicer = analysis.getSlicer().applyForSeries(newStock);
		StockAnalysis newAnalysis = new StockAnalysis(newStock,analysis.getApplyedCriterion(),newSlicer, analysis.getEvaluatorFactory(), analysis.getRunnerFactory());
		newAnalysis.getAdditionalCriteria().addAll(analysis.getAdditionalCriteria());
		
		//TODO Arrebentamos com o encapsulamento !
		newAnalysis.getReports().addAll(analysis.getReports());
		newAnalysis.reloadReports();
		
		return newAnalysis;

	}
}
