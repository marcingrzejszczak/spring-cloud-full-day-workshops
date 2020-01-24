package com.example.demo;

import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootTest
class DemoApplicationTests {

	@Autowired
	ApplicationContext context;

	@Autowired
	Environment environment;

	@Test
	void contextLoads() {
		BDDAssertions.then(context.getParent()).isNotNull();
		BDDAssertions.then(context.getParent().getId()).isEqualTo("bootstrap");

//		BDDAssertions.then(environment.getProperty("hello")).isEqualTo("darkness my old friend");
	}
}
