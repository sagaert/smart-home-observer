package org.salex.hmip.observer.service;

import org.salex.hmip.observer.blog.Image;
import org.salex.hmip.observer.data.ClimateMeasurement;
import org.salex.hmip.observer.data.ClimateMeasurementBoundaries;
import org.salex.hmip.observer.data.Reading;
import org.salex.hmip.observer.data.Sensor;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface ContentGenerator {
    Mono<String> generateOverview(Reading reading);

    Mono<String> generateDetails(Date start, Date end, Map<Sensor, List<ClimateMeasurement>> data, Image diagram);

    Mono<String> generateHistory(Date start, Date end, Map<Sensor, List<ClimateMeasurementBoundaries>> data, Map<Sensor, Map<String, Image>> diagrams);

    Mono<String> generateClimateAlert(Date start, Date end, Map<Sensor, List<ClimateMeasurement>> data);

    Mono<String> generateOperatingAlert(List<OperatingAlertService.Event> data);
}
