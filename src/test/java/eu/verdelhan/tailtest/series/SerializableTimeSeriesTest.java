package net.sf.tail.series;

import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import net.sf.tail.TimeSeries;
import net.sf.tail.io.StockSerializer;
import net.sf.tail.io.reader.CedroTimeSeriesLoader;
import net.sf.tail.series.SerializableTimeSeries;

import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class SerializableTimeSeriesTest {

	private SerializableTimeSeries stock;
	private TimeSeries series;

	@Before 
	public void setUp() throws FileNotFoundException, IOException{
		this.stock = new SerializableTimeSeries("Teste", "BaseBovespa/15min/ambv4.csv", new CedroTimeSeriesLoader());
		this.series = new CedroTimeSeriesLoader().load(new FileInputStream("BaseBovespa/15min/ambv4.csv"), "Teste");
	}
	
	@Test
	public void testGetName() {
		assertEquals("Teste", stock.getName());
	}

	@Test
	public void testGetSeriesAdress() {
		assertEquals("BaseBovespa/15min/ambv4.csv", stock.getSeriesAdress());
	}

	@Test
	public void testGetSeries() throws IOException {
		assertEquals(series, stock.getSeries());
	}
	
	@Test
	public void testReloadSeries() throws IOException{
		StockSerializer serializer = new StockSerializer();
		String serialized = serializer.toXML(stock);
		SerializableTimeSeries afterSerializedStock = serializer.fromXML(serialized);
		assertEquals(stock.getSeries(), afterSerializedStock.reloadSeries());
		assertEquals(stock.getSeries(), afterSerializedStock.getSeries());
	}
	@Test
	public void testGetPeriod()
	{
		assertEquals(new Period().withMinutes(15), stock.getPeriod());
	}

}
