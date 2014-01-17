package eu.verdelhan.tailtest.series;

import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

import eu.verdelhan.tailtest.TimeSeries;

public class SerializableTimeSeriesTest {

    private SerializableTimeSeries stock;
    private TimeSeries series;

    @Before
    public void setUp() throws FileNotFoundException, IOException {
//        stock = new SerializableTimeSeries("Teste", "BaseBovespa/15min/ambv4.csv", new CedroTimeSeriesLoader());
//        series = new CedroTimeSeriesLoader().load(new FileInputStream("BaseBovespa/15min/ambv4.csv"), "Teste");
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
    public void testGetPeriod() {
        assertEquals(new Period().withMinutes(15), stock.getPeriod());
    }

}
