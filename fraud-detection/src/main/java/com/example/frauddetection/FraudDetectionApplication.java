package com.example.frauddetection;

import java.util.List;
import java.util.function.Supplier;

import brave.propagation.ExtraFieldPropagation;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.sleuth.annotation.ContinueSpan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableConfigurationProperties(FraudProperties.class)
public class FraudDetectionApplication {

	public static void main(String[] args) {
		SpringApplication.run(FraudDetectionApplication.class, args);
	}

	@Bean
	MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer(@Value("${spring.application.name}") String applicationName) {
		return registry -> registry.config().commonTags("application", applicationName);
	}
}

@RestController
class FraudDetectionController {

	private static final Logger log = LoggerFactory.getLogger(FraudDetectionController.class);

	private final UriQueriedEmitter emitter;

	private final FraudProperties fraudProperties;

	private final DistributionSummary distributionSummary;

	FraudDetectionController(UriQueriedEmitter emitter, FraudProperties fraudProperties, MeterRegistry meterRegistry) {
		this.emitter = emitter;
		this.fraudProperties = fraudProperties;
		this.distributionSummary = meterRegistry.summary("frauds");
	}

	@GetMapping("/frauds")
	List<String> frauds(@RequestHeader(value = "x-my-special-key", required = false) String value) {
		emitter.uriQueriedForPath("/frauds");
		log.info("\n\nGot fraud request\n\n");
		log.info("\n\n[baggage] Request from user [{}]\n\n", ExtraFieldPropagation.get("user"));
		log.info("\n\n[propagation] Got the propagated header [{}]\n\n", value);
		List<String> frauds = this.fraudProperties.getList();
		measure(frauds);
		return frauds;
	}

	void measure(List<String> frauds) {
		this.distributionSummary.record(frauds.size());
	}
}

@ConfigurationProperties("fraud")
class FraudProperties {
	/**
	 * Describes the list of frauds.
	 */
	private List<String> list;

	public List<String> getList() {
		return this.list;
	}

	public void setList(List<String> list) {
		this.list = list;
	}
}


@Configuration
class Config {
	@Bean
	EmitterProcessor<UriQueried> proxyEventEmitterProcessor() {
		return EmitterProcessor.create();
	}

	@Bean
	Supplier<Flux<UriQueried>> events(EmitterProcessor<UriQueried> emitterProcessor) {
		return () -> emitterProcessor;
	}

	@Bean
	UriQueriedEmitter uriQueriedEmitter(Environment environment, EmitterProcessor<UriQueried> processor) {
		return new UriQueriedEmitter(environment, processor);
	}
}

class UriQueriedEmitter {

	private static final Logger log = LoggerFactory.getLogger(UriQueriedEmitter.class);

	private final Environment environment;

	private final EmitterProcessor<UriQueried> processor;

	UriQueriedEmitter(Environment environment, EmitterProcessor<UriQueried> processor) {
		this.environment = environment;
		this.processor = processor;
	}

	@ContinueSpan(log = "uri-queried")
	void uriQueriedForPath(String path) {
		String uri = "http://localhost:" + environment.getProperty("server.port") + path;
		UriQueried uriQueried = new UriQueried(environment.getProperty("spring.application.name"), uri);
		log.info("Sending out [{}]", uriQueried);
		this.processor.onNext(uriQueried);
	}
}

class UriQueried {

	private final String applicationName;

	private final long timestamp = System.currentTimeMillis();

	private final String uri;

	public UriQueried(String applicationName, String uri) {
		this.applicationName = applicationName;
		this.uri = uri;
	}

	public String getApplicationName() {
		return this.applicationName;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public String getUri() {
		return this.uri;
	}

	@Override
	public String toString() {
		return "UriQueried{" +
				"applicationName='" + applicationName + '\'' +
				", timestamp=" + timestamp +
				", uri='" + uri + '\'' +
				'}';
	}
}