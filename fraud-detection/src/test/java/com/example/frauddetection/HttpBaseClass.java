package com.example.frauddetection;

import java.util.Arrays;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.Mockito.mock;

/**
 * @author Marcin Grzejszczak
 * @since
 */
public abstract class HttpBaseClass {
	@BeforeEach
	void setup() {
		RestAssuredMockMvc.standaloneSetup(new FraudDetectionController(mock(UriQueriedEmitter.class),
				fraudProperties(), mock(MeterRegistry.class)) {
			@Override
			void measure(List<String> list) {
			}
		});
	}

	private FraudProperties fraudProperties() {
		FraudProperties fraudProperties = new FraudProperties();
		fraudProperties.setList(Arrays.asList("olga", "oleg"));
		return fraudProperties;
	}
}
