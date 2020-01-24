package com.example.reporting;

import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.StubTrigger;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

@SpringBootTest(classes = {MessagingTest.Config.class, ReportingApplication.class},
		webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureStubRunner(ids = "com.example:fraud-detection",
		stubsPerConsumer = true,
		stubsMode = StubRunnerProperties.StubsMode.REMOTE,
		repositoryRoot = "http://localhost:8081/artifactory/libs-release-local"
)
class MessagingTest {

	@Autowired
	StubTrigger stubTrigger;

	@Autowired
	UriQueriedReactiveRepository repository;

	@Test
	void should_reject_a_loan_for_a_fraud() {
		stubTrigger.trigger("trigger_fraud_uri");

		BDDMockito.then(repository).should(BDDMockito.atLeastOnce())
				.save(BDDMockito.argThat(argument ->
								"fraud-detection".equals(argument.getApplicationName()) &&
										StringUtils.hasText(argument.getUri()) &&
										argument.getTimestamp() != 0
						)
				);
	}

	@Configuration
	@Import(TestChannelBinderConfiguration.class)
	static class Config {
		@Bean
		@Primary
		UriQueriedReactiveRepository mockUriQueriedReactiveRepository() {
			return BDDMockito.mock(UriQueriedReactiveRepository.class);
		}
	}

}