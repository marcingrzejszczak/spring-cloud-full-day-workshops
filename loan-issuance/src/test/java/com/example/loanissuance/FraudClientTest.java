package com.example.loanissuance;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;

/**
 * @author Marcin Grzejszczak
 * @since
 */
@AutoConfigureStubRunner(ids = "com.example:fraud-detection",
		stubsPerConsumer = true,
		stubsMode = StubRunnerProperties.StubsMode.REMOTE,
		repositoryRoot = "http://localhost:8081/artifactory/libs-release-local",
		generateStubs = true)
@SpringBootTest
class FraudClientTest {
	@Autowired
	LoanIssuanceController controller;

	@Test
	void should_reject_a_loan_for_a_fraud() {
		ResponseEntity<String> responseEntity = controller.loan(new LoanApplication("olga")).block();

		BDDAssertions.then(responseEntity.getBody()).isEqualTo("LOAN_REJECTED");
	}

	@Test
	void should_grant_a_loan_for_a_non_fraud() {
		StepVerifier.create(controller.loan(new LoanApplication("marcin"))
				.map(HttpEntity::getBody))
				.expectNext("LOAN_GRANTED")
				.verifyComplete();
	}
}
