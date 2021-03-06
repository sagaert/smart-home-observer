package org.salex.hmip.observer.service;

import org.salex.hmip.observer.data.ClimateMeasurement;
import org.salex.hmip.observer.data.Sensor;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface MailPublishService {
    Mono<Map<Sensor, List<ClimateMeasurement>>> sendClimateAlert(Date start, Date end, Map<Sensor, List<ClimateMeasurement>> data);

    Mono<List<OperatingAlertService.Event>> sendOperatingAlert(List<OperatingAlertService.Event> data);
}
