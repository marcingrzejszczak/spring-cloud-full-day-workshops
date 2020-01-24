package com.example.reporting;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import brave.propagation.ExtraFieldPropagation;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
// @EnableScheduling
public class ReportingApplication {

	private static final Logger log = LoggerFactory.getLogger(ReportingApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(ReportingApplication.class, args);
	}

	@Bean
	Consumer<Mono<UriQueried>> events(UriQueriedReactiveRepository repository) {
		return uri -> uri
				.doOnNext(uriQueried -> log.info("Got a message [{}]", uriQueried))
				.doOnNext(uriQueried -> log.info("Baggage from user [{}]", ExtraFieldPropagation.get("user")))
				.flatMap(repository::save).subscribe();
	}

	@Bean
	MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer(@Value("${spring.application.name}") String applicationName) {
		return registry -> registry.config().commonTags("application", applicationName);
	}
}

@RestController
class UriQueriedController {
	private final UriQueriedReactiveRepository repository;

	private final AtomicLong gauge;

	UriQueriedController(UriQueriedReactiveRepository repository, MeterRegistry meterRegistry) {
		this.repository = repository;
		this.gauge = meterRegistry.gauge("queries", new AtomicLong());
	}

	@GetMapping("/query")
	Flux<UriQueried> queries() {
		return this.repository.findAll();
	}

	@GetMapping("/count")
	Mono<Long> count() {
		return this.repository.count();
	}

	@GetMapping(value = "/queryStream", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	Flux<UriQueried> queriesStreamFromLastTime() {
		final long millis = 1000;
		return Flux.interval(Duration.ofSeconds(millis / 1000))
				.flatMap(id -> this.repository.findAllByTimestampIsAfter(System.currentTimeMillis() - millis));
	}

	@Scheduled(fixedRate = 1000L)
	void updateGauge() {
		Flux.from(this.count())
				.doOnNext(gauge::set)
				.subscribe();
	}
}

interface UriQueriedReactiveRepository extends ReactiveCrudRepository<UriQueried, String> {

	Flux<UriQueried> findAllByTimestampIsAfter(long timestamp);
}


@Document
class UriQueried {

	@Id
	private String id;

	private String applicationName;

	private long timestamp;

	private String uri;

	public UriQueried() {
	}

	public UriQueried(String applicationName, long timestamp, String uri) {
		this.applicationName = applicationName;
		this.timestamp = timestamp;
		this.uri = uri;
	}

	public String getApplicationName() {
		return this.applicationName;
	}

	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	@Override
	public String toString() {
		return "UriQueried{" +
				"applicationName=" + applicationName +
				", id=" + id +
				", timestamp=" + timestamp +
				", uri='" + uri + '\'' +
				'}';
	}
}