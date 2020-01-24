package com.example.frauddetection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Marcin Grzejszczak
 * @since
 */
@SpringBootTest(classes = {MessagingBaseClass.Config.class, FraudDetectionApplication.class},
		webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "server.port=7654")
@AutoConfigureMessageVerifier
public abstract class MessagingBaseClass {

	@Autowired
	FraudDetectionController controller;

	public void frauds() {
		this.controller.frauds("");
	}

	@Configuration
	@Import(TestChannelBinderConfiguration.class)
	static class Config {

	}
}
