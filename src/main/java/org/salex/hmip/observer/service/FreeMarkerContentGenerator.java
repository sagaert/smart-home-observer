package org.salex.hmip.observer.service;

import freemarker.core.TemplateNumberFormatFactory;
import freemarker.template.Template;
import org.salex.hmip.observer.blog.Image;
import org.salex.hmip.observer.data.*;
import org.springframework.web.reactive.result.view.freemarker.FreeMarkerConfigurer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.StringWriter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FreeMarkerContentGenerator implements ContentGenerator {
    private static final String TIMESTAMP_FORMAT = "dd.MM.yyyy HH:mm";
    private static final String DATE_FORMAT = "dd.MM.yyyy";
    private static final String TIME_FORMAT = "HH:mm";

    private final Template overviewTemplate;

    private final Template detailsTemplate;

    private final Template historyTemplate;

    private final Template climateAlertTemplate;

    private final Template operatingAlertTemplate;

    public FreeMarkerContentGenerator(FreeMarkerConfigurer freeMarkerConfigurer) throws Exception {
        final var customNumberFormats = new HashMap<String, TemplateNumberFormatFactory>();
        customNumberFormats.put("temp", FreeMarkerTemperatureFormat.factory());
        customNumberFormats.put("hum", FreeMarkerHumidityFormat.factory());
        customNumberFormats.put("memUsage", FreeMarkerMemUsageFormat.factory());
        customNumberFormats.put("cpuTemp", FreeMarkerCPUTemperatureFormat.factory());

        this.overviewTemplate = freeMarkerConfigurer.getConfiguration().getTemplate("blog/wordpress/overview.ftl");
        this.overviewTemplate.setDateFormat(DATE_FORMAT);
        this.overviewTemplate.setTimeFormat(TIME_FORMAT);
        this.overviewTemplate.setCustomNumberFormats(customNumberFormats);

        this.detailsTemplate = freeMarkerConfigurer.getConfiguration().getTemplate("blog/wordpress/details.ftl");
        this.detailsTemplate.setDateTimeFormat(TIMESTAMP_FORMAT);
        this.detailsTemplate.setCustomNumberFormats(customNumberFormats);

        this.historyTemplate = freeMarkerConfigurer.getConfiguration().getTemplate("blog/wordpress/history.ftl");
        this.overviewTemplate.setDateFormat(DATE_FORMAT);
        this.historyTemplate.setCustomNumberFormats(customNumberFormats);

        this.climateAlertTemplate = freeMarkerConfigurer.getConfiguration().getTemplate("mail/climate-alert.ftl");
        this.climateAlertTemplate.setDateTimeFormat(TIMESTAMP_FORMAT);
        this.climateAlertTemplate.setCustomNumberFormats(customNumberFormats);

        this.operatingAlertTemplate = freeMarkerConfigurer.getConfiguration().getTemplate("mail/operating-alert.ftl");
        this.operatingAlertTemplate.setDateTimeFormat(TIMESTAMP_FORMAT);
        this.operatingAlertTemplate.setCustomNumberFormats(customNumberFormats);
    }

    @Override
    public Mono<String> generateOverview(Reading reading) {
        final var templateData = new HashMap<String, Object>();
        templateData.put("readingTime", reading.getReadingTime());
        templateData.put("measurements", reading.getMeasurements().stream()
                .filter(m -> m instanceof ClimateMeasurement)
                .map(ClimateMeasurement.class::cast)
                .collect(Collectors.toList())
        );
        try {
            final var content = new StringWriter();
            this.overviewTemplate.process(templateData, content);
            return Mono.just(content.toString());
        } catch(Exception e) {
            return Mono.error(e);
        }
    }

    @Override
    public Mono<String> generateDetails(Date start, Date end, Map<Sensor, List<ClimateMeasurement>> data, Image diagram) {
        if(data.isEmpty()) {
            return Mono.just("<h3>Keine Daten vorhanden</h3>");
        }
        return Flux.fromIterable(data.keySet())
                .map(sensor -> {
                    final var templateData = new HashMap<String, Object>();
                    final var measurements = data.get(sensor);
                    templateData.put("minTemp", measurements.stream().min(Comparator.comparing(ClimateMeasurement::getTemperature)).orElseThrow());
                    templateData.put("maxTemp", measurements.stream().max(Comparator.comparing(ClimateMeasurement::getTemperature)).orElseThrow());
                    templateData.put("minHum", measurements.stream().min(Comparator.comparing(ClimateMeasurement::getHumidity)).orElseThrow());
                    templateData.put("maxHum", measurements.stream().max(Comparator.comparing(ClimateMeasurement::getHumidity)).orElseThrow());
                    templateData.put("sensor", sensor);
                    return templateData;
                })
                .collectList()
                .map(measurements -> {
                    final var templateData = new HashMap<String, Object>();
                    templateData.put("measurements", measurements);
                    templateData.put("periodStart", start);
                    templateData.put("periodEnd", end);
                    templateData.put("diagram", diagram);
                    return templateData;
                })
                .flatMap(templateData -> {
                    try {
                        final var content = new StringWriter();
                        this.detailsTemplate.process(templateData, content);
                        return Mono.just(content.toString());
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
    }

    @Override
    public Mono<String> generateHistory(Date start, Date end, Map<Sensor, List<ClimateMeasurementBoundaries>> data, Map<Sensor, Map<String, Image>> diagrams) {
        if(data.isEmpty()) {
            return Mono.just("<h3>Keine Daten vorhanden</h3>");
        }
        if(diagrams.isEmpty()) {
            return Mono.just("<h3>Keine Diagramme vorhanden</h3>");
        }
        return Flux.fromIterable(data.keySet())
                .map(sensor -> {
                    final var templateData = new HashMap<String, Object>();
                    final var measurements = data.get(sensor);
                    templateData.put("minTemp", measurements.stream().min(Comparator.comparing(ClimateMeasurementBoundaries::getMinimumTemperature)).orElseThrow());
                    templateData.put("maxTemp", measurements.stream().max(Comparator.comparing(ClimateMeasurementBoundaries::getMaximumTemperature)).orElseThrow());
                    templateData.put("minHum", measurements.stream().min(Comparator.comparing(ClimateMeasurementBoundaries::getMinimumHumidity)).orElseThrow());
                    templateData.put("maxHum", measurements.stream().max(Comparator.comparing(ClimateMeasurementBoundaries::getMaximumHumidity)).orElseThrow());
                    templateData.put("tempDiagram", diagrams.get(sensor).get("temperature"));
                    templateData.put("humDiagram", diagrams.get(sensor).get("humidity"));
                    templateData.put("sensor", sensor);
                    return templateData;
                })
                .collectList()
                .map(measurements -> {
                    final var templateData = new HashMap<String, Object>();
                    templateData.put("measurements", measurements);
                    templateData.put("periodStart", start);
                    templateData.put("periodEnd", end);
                    return templateData;
                })
                .flatMap(templateData -> {
                    try {
                        final var content = new StringWriter();
                        this.historyTemplate.process(templateData, content);
                        return Mono.just(content.toString());
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
    }

    @Override
    public Mono<String> generateClimateAlert(Date start, Date end, Map<Sensor, List<ClimateMeasurement>> data) {
        if(data.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(data.keySet())
                .filter(sensor -> !data.get(sensor).isEmpty())
                .map(sensor -> {
                    final var templateData = new HashMap<String, Object>();
                    final var measurements = data.get(sensor);
                    templateData.put("minTemp", measurements.stream().min(Comparator.comparing(ClimateMeasurement::getTemperature)).orElseThrow());
                    templateData.put("maxTemp", measurements.stream().max(Comparator.comparing(ClimateMeasurement::getTemperature)).orElseThrow());
                    templateData.put("minHum", measurements.stream().min(Comparator.comparing(ClimateMeasurement::getHumidity)).orElseThrow());
                    templateData.put("maxHum", measurements.stream().max(Comparator.comparing(ClimateMeasurement::getHumidity)).orElseThrow());
                    templateData.put("sensor", sensor);
                    return templateData;
                })
                .collectList()
                .map(boundaries -> {
                    final var templateData = new HashMap<String, Object>();
                    templateData.put("boundaries", boundaries);
                    templateData.put("periodStart", start);
                    templateData.put("periodEnd", end);
                    return templateData;
                })
                .flatMap(templateData -> {
                    try {
                        final var content = new StringWriter();
                        this.climateAlertTemplate.process(templateData, content);
                        return Mono.just(content.toString());
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
    }

    @Override
    public Mono<String> generateOperatingAlert(List<OperatingAlertService.Event> data) {
        return Mono.just(new HashMap<String, Object>())
                .map(templateData -> {
                    templateData.put("exceedances", data.stream()
                            .filter(event -> event instanceof OperatingAlertService.Exceedance)
                            .map(OperatingAlertService.Exceedance.class::cast)
                            .collect(Collectors.toList()));
                    templateData.put("errors", data.stream()
                            .filter(event -> event instanceof OperatingAlertService.Error)
                            .map(OperatingAlertService.Error.class::cast)
                            .collect(Collectors.toList()));
                    return templateData;
                })
                .flatMap(templateData -> {
                    try {
                        final var content = new StringWriter();
                        this.operatingAlertTemplate.process(templateData, content);
                        return Mono.just(content.toString());
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
    }
}
