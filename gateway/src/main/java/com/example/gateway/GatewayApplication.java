package com.example.gateway;

import java.util.function.Supplier;

import brave.CurrentSpanCustomizer;
import brave.Span;
import brave.Tracer;
import brave.propagation.ExtraFieldPropagation;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

@SpringBootApplication
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	@Bean
	MeterRegistryCustomizer<MeterRegistry> meterRegistryCustomizer(@Value("${spring.application.name}") String applicationName) {
		return registry -> registry.config().commonTags("application", applicationName);
	}
}

@Configuration
@Profile("code")
class CodeConfiguration {

	@Bean
	RouteLocator myRoute(RouteLocatorBuilder builder,  @Value("${stripe.auth.header:sk_test_4eC39HqLyjWDarjtT1zdp7dc}") String authorizationHeader) {
		return builder.routes()
				.route(s ->
					// localhost:9083/apply/loan
					//  strip prefix 1
					// circuit breaker
					// loan-issuance/loan
					s.path("/apply/**")
							.filters(g ->
									g.stripPrefix(1)
							.circuitBreaker(config -> config.setName("loan")))
					.uri("lb://loan-issuance")
				)
				// localhost:9083/credit/charges
					// authorization
				// https://api.stripe.com/v1/charges
				.route(s ->
					s.path("/credit/**")
						.filters(g ->
								g.stripPrefix(1)
								.prefixPath("/v1")
								.addRequestHeader("Authorization", "Bearer " + authorizationHeader)
								.circuitBreaker(config -> config.setName("credit"))
						)
						.uri("https://api.stripe.com/")
				)
				.build();
	}
}


@Configuration
class Common {

	@Bean
	SendAMessageFilter sendAMessageFilter(UriQueriedEmitter emitter) {
		return new SendAMessageFilter(emitter);
	}

	@Bean
	EmitterProcessor<UriQueried> proxyEventEmitterProcessor() {
		return EmitterProcessor.create();
	}

	@Bean
	Supplier<Flux<UriQueried>> proxyEvents(EmitterProcessor<UriQueried> emitterProcessor) {
		return () -> emitterProcessor;
	}

	@Bean
	UriQueriedEmitter uriQueriedEmitter(Environment environment, Tracer tracer, CurrentSpanCustomizer currentSpanCustomizer, EmitterProcessor<UriQueried> processor) {
		return new UriQueriedEmitter(environment, processor, tracer, currentSpanCustomizer);
	}
}

class SendAMessageFilter implements GlobalFilter, Ordered {

	private final UriQueriedEmitter emitter;

	SendAMessageFilter(UriQueriedEmitter emitter) {
		this.emitter = emitter;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String uri = exchange.getRequest().getURI().toString();
		return chain.filter(exchange).doOnSuccessOrError((aVoid, throwable) -> this.emitter.uriQueriedForUri(uri));
	}

	@Override
	public int getOrder() {
		return 0;
	}
}


class UriQueriedEmitter {

	private static final Logger log = LoggerFactory.getLogger(UriQueriedEmitter.class);

	private final Environment environment;

	private final EmitterProcessor<UriQueried> processor;

	private final Tracer tracer;

	private final CurrentSpanCustomizer customizer;

	UriQueriedEmitter(Environment environment, EmitterProcessor<UriQueried> processor, Tracer tracer, CurrentSpanCustomizer customizer) {
		this.environment = environment;
		this.processor = processor;
		this.tracer = tracer;
		this.customizer = customizer;
	}

	void uriQueriedForUri(String uri) {
		Span span = tracer.nextSpan().name("my-custom-span").start();
		log.info("After span creation");
		try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
			// Show current span and context retrieval
			// tracer.currentSpan().context().traceId();
			this.customizer.annotate("my-annotation").name("changed-name").tag("key", "value");
			UriQueried uriQueried = new UriQueried(environment.getProperty("spring.application.name"), uri);
			log.info("Sending out [{}]", uriQueried);
			this.processor.onNext(uriQueried);
		} finally {
			log.info("Just before finish");
			span.finish();
		}
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
}

@Component
class MyBaggageFilter implements GlobalFilter {

	private static final Logger log = LoggerFactory.getLogger(MyBaggageFilter.class);

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ExtraFieldPropagation.set("user", "mg");
		log.info("Set the propagation value");
		return chain.filter(exchange);
	}
}