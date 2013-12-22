package net.sf.tail.io;

import java.io.IOException;

import net.sf.tail.report.Report;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

public class ReportSerializer {

	public String toXML(Report report) {
		XStream xstream = new XStream(new DomDriver());
		xstream.setMode(XStream.NO_REFERENCES);
		return xstream.toXML(report);

	}

	@SuppressWarnings("unchecked")
	public Report fromXML(String xml) throws IOException {
		XStream xstream = new XStream(new DomDriver());
		Report report = (Report) xstream.fromXML(xml);
		return report;
	}
}
