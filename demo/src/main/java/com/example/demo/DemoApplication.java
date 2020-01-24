package com.example.demo;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@SpringBootApplication
@EnableConfigurationProperties(MyProperty.class)
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}
}

@RestController
@RefreshScope
class MyController {
	final MyProperty myProperty;
	final String myProp;
	final MyRefreshedBean myRefreshedBean;

	MyController(MyProperty myProperty, MyRefreshedBean myRefreshedBean, @Value("${my.property:fixed}") String myProp) {
		this.myProperty = myProperty;
		this.myProp = myProp;
		this.myRefreshedBean = myRefreshedBean;
	}

	@GetMapping("/example")
	String example() {
		return "From config props [" + this.myProperty.getProperty() + "] from refreshed bean [" + this.myRefreshedBean.increment() + "] from value [" + this.myProp + "]";
	}
}


@ConfigurationProperties("my")
class MyProperty {
	private String property;

	public String getProperty() {
		System.out.println("Getting the value");
		return property;
	}

	public void setProperty(String property) {
		System.out.println("Setting the value");
		this.property = property;
	}
}

@RefreshScope
@Component
class MyRefreshedBean {
	private final AtomicInteger counter = new AtomicInteger();

	public int increment() {
		return counter.incrementAndGet();
	}
}

@Component
class MyEnvironmentChangeEventListener implements ApplicationListener<EnvironmentChangeEvent> {

	private static final Logger log = LoggerFactory.getLogger(MyEnvironmentChangeEventListener.class);

	@Override
	public void onApplicationEvent(EnvironmentChangeEvent event) {
		log.info("I got the event! {}", event.toString());
		log.debug("Special debug message");
	}
}
