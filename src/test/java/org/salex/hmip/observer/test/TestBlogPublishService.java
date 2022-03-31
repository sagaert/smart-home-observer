package org.salex.hmip.observer.test;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.salex.hmip.observer.blog.Image;
import org.salex.hmip.observer.data.ClimateMeasurement;
import org.salex.hmip.observer.data.OperatingMeasurement;
import org.salex.hmip.observer.data.Reading;
import org.salex.hmip.observer.data.Sensor;
import org.salex.hmip.observer.service.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class TestBlogPublishService {
    private MockWebServer mockWebServer;
    private WebClient webClient;
    private ContentGenerator contentGenerator;
    private ChartGenerator chartGenerator;

    @BeforeEach
    void setup() throws IOException {
        this.mockWebServer = new MockWebServer();
        this.mockWebServer.start();
        this.webClient = WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build();
        this.contentGenerator = mock(ContentGenerator.class);
        this.chartGenerator = mock(ChartGenerator.class);
    }

    @AfterEach
    void tearDown() throws IOException {
        this.mockWebServer.shutdown();
    }

    @Test
    void should_generate_overview_for_reading() throws Exception {
        // Prepare the test data
        final var now = new Date();
        final var reading = new Reading(now);
        final var firstSensor = new Sensor(1L, "Testsensor 1", Sensor.Type.HmIP_STHO, "test-sgtin-1", "#FF0000");
        final var secondSensor = new Sensor(2L, "Testsensor 2", Sensor.Type.HmIP_STHO, "test-sgtin-2", "#00FF00");
        reading.addMeasurement(new ClimateMeasurement(reading, firstSensor, now, 12.3, 42.7, 3.45674395764));
        reading.addMeasurement(new ClimateMeasurement(reading, secondSensor, now, 15.7, 43.5, 3.14159265358));
        reading.addMeasurement(new OperatingMeasurement(reading, 1.0, 2.0, 3.0, 4.0));

        // Prepare the mocks
        when(contentGenerator.generateOverview(reading)).thenReturn(Mono.just("some test content"));
        this.mockWebServer.enqueue(createMockResponse(HttpStatus.OK, "overview-content-block.json", new String[][] { { "Content-Type", "application/json; charset=UTF-8" } } )); // Read old content
        this.mockWebServer.enqueue(createMockResponse(HttpStatus.OK, null)); // Post new content

        // Create and call the service
        final var service = new WordPressPublishService(webClient, contentGenerator, chartGenerator);
        StepVerifier
                .create(service.postOverview(reading))
                .expectNextCount(1)
                .verifyComplete();

        // Verfication
        verify(contentGenerator, times(1)).generateOverview(reading);
        verifyNoMoreInteractions(contentGenerator);
        verifyNoInteractions(chartGenerator);
        assertThat(this.mockWebServer.getRequestCount()).isEqualTo(2);
        final var readPostRequest = this.mockWebServer.takeRequest();
        final var updatePostRequest = this.mockWebServer.takeRequest();
        assertThat(readPostRequest.getMethod()).isEqualTo("GET");
        assertThat(readPostRequest.getPath()).isEqualTo("/content_block/146");
        assertThat(updatePostRequest.getMethod()).isEqualTo("POST");
        assertThat(updatePostRequest.getPath()).isEqualTo("/content_block/146");
    }

    @Test
    void should_generate_details_for_period() throws Exception {
        // Prepare the test data
        final var now = new Date();
        final var tenMinutesAgo = new Date(now.getTime() - TimeUnit.MINUTES.toMillis(10));
        final var twentyMinutesAgo = new Date(now.getTime() - TimeUnit.MINUTES.toMillis(20));
        final var reading = new Reading(now);
        final var image = new Image("test-image");
        image.setFull("http://link-to-test-image");
        final var firstSensor = new Sensor(1L, "First", Sensor.Type.HmIP_STHO, "test-sgtin-1", "#FF0000");
        final var secondSensor = new Sensor(2L, "Second", Sensor.Type.HmIP_STHO, "test-sgtin-2", "#00FF00");
        final var data = Map.of(
                firstSensor, List.of(
                        new ClimateMeasurement(reading, firstSensor, twentyMinutesAgo, 11.2, 52.7, 5.2386758493768),
                        new ClimateMeasurement(reading, firstSensor, tenMinutesAgo, 13.2, 42.7, 5.2386758493768),
                        new ClimateMeasurement(reading, firstSensor, now, 12.2, 32.7, 5.2386758493768)
                ),
                secondSensor, List.of(
                        new ClimateMeasurement(reading, secondSensor, twentyMinutesAgo, 21.2, 82.7, 5.2386758493768),
                        new ClimateMeasurement(reading, secondSensor, tenMinutesAgo, 23.2, 72.7, 5.2386758493768),
                        new ClimateMeasurement(reading, secondSensor, now, 22.2, 62.7, 5.2386758493768)
                )
        );

        // Prepare the mocks
        when(chartGenerator.create24HourChart(any(), any(), any())).thenReturn(new byte[0]);
        when(contentGenerator.generateDetails(any(), any(), any(), any(Image.class))).thenReturn(Mono.just("some test content"));
        this.mockWebServer.enqueue(createMockResponse(HttpStatus.CREATED, "add-image-result.json", new String[][] { { "Location", "some-test-id/12345" }, { "Content-Type", "application/json; charset=UTF-8" }})); // Add new image
        this.mockWebServer.enqueue(createMockResponse(HttpStatus.OK, "get-image-result.json", new String[][] { { "Content-Type", "application/json; charset=UTF-8" } })); // Read data for new image
        this.mockWebServer.enqueue(createMockResponse(HttpStatus.OK, "details-page.json", new String[][] { { "Content-Type", "application/json; charset=UTF-8" } })); // Read old content
        this.mockWebServer.enqueue(createMockResponse(HttpStatus.OK, null)); // Post new content
        this.mockWebServer.enqueue(createMockResponse(HttpStatus.OK, null)); // Delete image 634535

        // Create and call the service
        final var service = new WordPressPublishService(webClient, contentGenerator, chartGenerator);
        StepVerifier
                .create(service.postDetails(tenMinutesAgo, now, data))
                .expectNextCount(1)
                .verifyComplete();

        // Verification
        verify(contentGenerator, times(1)).generateDetails(any(), any(), any(), any(Image.class));
        verifyNoMoreInteractions(contentGenerator);
        verify(chartGenerator, times(1)).create24HourChart(any(), any(), any());
        verifyNoMoreInteractions(chartGenerator);
        assertThat(this.mockWebServer.getRequestCount()).isEqualTo(5);
        final var addNewImageRequest = this.mockWebServer.takeRequest();
        final var readNewImageRequest = this.mockWebServer.takeRequest();
        final var readPostRequest = this.mockWebServer.takeRequest();
        final var updatePostRequest = this.mockWebServer.takeRequest();
        final var deleteOldImageRequest = this.mockWebServer.takeRequest();
        assertThat(addNewImageRequest.getMethod()).isEqualTo("POST");
        assertThat(addNewImageRequest.getPath()).isEqualTo("/media");
        assertThat(addNewImageRequest.getHeader("content-disposition")).startsWith("attachement; filename=verlauf-");
        assertThat(readNewImageRequest.getMethod()).isEqualTo("GET");
        assertThat(readNewImageRequest.getPath()).isEqualTo("/media/12345");
        assertThat(readPostRequest.getMethod()).isEqualTo("GET");
        assertThat(readPostRequest.getPath()).isEqualTo("/pages/148");
        assertThat(updatePostRequest.getMethod()).isEqualTo("POST");
        assertThat(updatePostRequest.getPath()).isEqualTo("/pages/148");
        assertThat(deleteOldImageRequest.getMethod()).isEqualTo("DELETE");
        assertThat(deleteOldImageRequest.getPath()).isEqualTo("/media/634535?force=true");
    }

    private static MockResponse createMockResponse(HttpStatus status, String resultJson, String[]... headers) throws IOException {
        final var mockResponse = new MockResponse();
        mockResponse.setResponseCode(status.value());
        if(resultJson != null) {
            final var resultJsonFile = new ClassPathResource("TestBlogPublishService/" + resultJson).getFile();
            final var body = Files.readString(resultJsonFile.toPath());
            mockResponse.setBody(body);
        }
        for(var header : headers) {
            mockResponse.setHeader(header[0], header[1]);
        }
        return mockResponse;
    }
}
