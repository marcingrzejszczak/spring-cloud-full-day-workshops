package com.example.configclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class ConfigClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConfigClientApplication.class, args);
	}

}

@RestController
class Client {

	private final Environment environment;

	Client(Environment environment) {
		this.environment = environment;
	}

	@GetMapping("/environment/{property}")
	String property(@PathVariable String property) {
		return this.environment.getProperty(property);
	}
}