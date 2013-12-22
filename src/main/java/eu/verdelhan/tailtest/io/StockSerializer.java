package net.sf.tail.io;

import java.io.IOException;

import net.sf.tail.series.SerializableTimeSeries;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

public class StockSerializer {

	public String toXML(SerializableTimeSeries stock) {
		XStream xstream = new XStream(new DomDriver());
		xstream.setMode(XStream.NO_REFERENCES);
		return xstream.toXML(stock);

	}

	@SuppressWarnings("unchecked")
	public SerializableTimeSeries fromXML(String xml) throws IOException {
		XStream xstream = new XStream(new DomDriver());
		SerializableTimeSeries stock = (SerializableTimeSeries) xstream.fromXML(xml);
		stock.reloadSeries();
		return stock;

	}
}
