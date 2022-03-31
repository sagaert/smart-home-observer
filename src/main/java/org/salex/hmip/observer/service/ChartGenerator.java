package org.salex.hmip.observer.service;

import org.salex.hmip.observer.data.ClimateMeasurement;
import org.salex.hmip.observer.data.Sensor;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

public interface ChartGenerator {
    byte[] create24HourChart(Date start, Date end, Map<Sensor, List<ClimateMeasurement>> data) throws IOException;

    byte[] create365DayTemperatureChart(Date start, Date end, List<ClimateMeasurement> data, Sensor sensor) throws IOException;

    byte[] create365DayHumidityChart(Date start, Date end, List<ClimateMeasurement> data, Sensor sensor) throws IOException;
}
