package com.example.loanissuance;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import brave.propagation.ExtraFieldPropagation;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.cache.LoadBalancerCacheManager;
import org.springframework.cloud.loadbalancer.core.CachingServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.sleuth.annotation.NewSpan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;


@SpringBootApplication
@EnableAsync
public class LoanIssuanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoanIssuanceApplication.class, args);
	}

	@Bean
	@Primary
	WebClient.Builder nonLbWebClientBuilder() {
		return WebClient.builder();
	}

	@Bean
	@LoadBalanced
	WebClient.Builder webClient() {
		return WebClient.builder();
	}

	@Bean
	MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer(@Value("${spring.application.name}") String applicationName) {
		return registry -> registry.config().commonTags("application", applicationName);
	}

	@Bean
	TimedAspect timedAspect(MeterRegistry registry) {
		return new TimedAspect(registry);
	}

	@Bean
	CountedAspect countedAspect(MeterRegistry registry) {
		return new CountedAspect(registry);
	}
}

// explain proxy bean methods
@Configuration(proxyBeanMethods = false)
class CircuitConfiguration {

	@Bean
	Customizer<Resilience4JCircuitBreakerFactory> resilience4JCircuitBreakerFactoryCustomizer() {
		return f -> {
			f.configureDefault(id -> new Resilience4JConfigBuilder(id)
					.timeLimiterConfig(TimeLimiterConfig.custom()
							.timeoutDuration(Duration.ofSeconds(2))
							.cancelRunningFuture(true)
							.build())
					.circuitBreakerConfig(CircuitBreakerConfig.custom()
							.minimumNumberOfCalls(5)
							.failureRateThreshold(1)
							.waitDurationInOpenState(Duration.ofSeconds(5))
							.permittedNumberOfCallsInHalfOpenState(5)
							.build())
					.build());
		};
	}

}

@RestController
class LoanIssuanceController {

	private static final Logger log = LoggerFactory.getLogger(LoanIssuanceController.class);

	private final WebClient webClient;

	private final UriQueriedEmitter emitter;

	private final ReactiveCircuitBreakerFactory factory;

	LoanIssuanceController(@LoadBalanced WebClient.Builder builder, UriQueriedEmitter emitter, ReactiveCircuitBreakerFactory factory) {
		this.webClient = builder.build();
		this.emitter = emitter;
		this.factory = factory;
	}

	@PostMapping("/loan")
	@Counted(value = "loan.counted",description = "loan application retrieval")
	Mono<ResponseEntity> loan(@RequestBody LoanApplication loanApplication) {
		log.info("\n\nGot loan/ request\n\n");
		log.info("\n\n[baggage] Request from user [{}]\n\n", ExtraFieldPropagation.get("user"));
		this.emitter.uriQueriedForPath("/loan");
		return factory.create("fraud").run(this.webClient.get()
				.uri("http://fraud-detection/frauds")
				.retrieve()
				.bodyToMono(List.class)).map(frauds -> {
			System.out.println(frauds.contains(loanApplication.getName()));
			if (frauds != null && !frauds.contains(loanApplication.getName())) {
				return ResponseEntity.status(HttpStatus.OK).body("LOAN_GRANTED");
			}
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("LOAN_REJECTED");
		});
	}
}

class LoanApplication {
	private String name;

	LoanApplication(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}

@Configuration
@LoadBalancerClient(value = "fraud-detection", configuration = CustomLoadBalancerConfiguration.class)
class MyConfiguration {

}

class CustomLoadBalancerConfiguration {

	@Bean
	public ServiceInstanceListSupplier myServiceInstanceListSupplier(ApplicationContext context) {
		StaticServiceInstanceListSupplier delegate = new StaticServiceInstanceListSupplier();
		ObjectProvider<LoadBalancerCacheManager> cacheManagerProvider = context
				.getBeanProvider(LoadBalancerCacheManager.class);
		if (cacheManagerProvider.getIfAvailable() != null) {
			return new CachingServiceInstanceListSupplier(delegate,
					cacheManagerProvider.getIfAvailable());
		}
		return delegate;
	}
}

class StaticServiceInstanceListSupplier implements ServiceInstanceListSupplier {

	@Override
	public String getServiceId() {
		return "fraud-detection";
	}

	@Override
	public Flux<List<ServiceInstance>> get() {
		System.out.println("Custom load balancer service instance list supplier");
		return Flux.just(Collections.singletonList(new ServiceInstance() {
			@Override
			public String getServiceId() {
				return "fraud-detection";
			}

			@Override
			public String getHost() {
				return "localhost";
			}

			@Override
			public int getPort() {
				return 9080;
			}

			@Override
			public boolean isSecure() {
				return false;
			}

			@Override
			public URI getUri() {
				return URI.create("http://localhost:9080");
			}

			@Override
			public Map<String, String> getMetadata() {
				return null;
			}
		}));
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

	@NewSpan("query-uri")
	@Async
	@Timed(value = "message_sending", percentiles = {0.5, 0.99}, histogram = true)
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