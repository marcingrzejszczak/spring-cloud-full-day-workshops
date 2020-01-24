package com.example.loanissuancemvc;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.mvc.ProxyExchange;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;


@EnableFeignClients
@SpringBootApplication
public class LoanIssuanceMvcApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoanIssuanceMvcApplication.class, args);
	}

	@Bean
	@Primary
	RestTemplate nonLBRestTemplate() {
		return new RestTemplate();
	}

	@Bean
	@LoadBalanced
	RestTemplate restTemplate() {
		return new RestTemplate();
	}

}

@RestController
class MyProxy {
	@GetMapping("/credit/**")
	public ResponseEntity<?> proxy(ProxyExchange<byte[]> proxy) throws Exception {
		String path = proxy.path("/credit/");
		return proxy.sensitive().header("Authorization", "Bearer sk_test_4eC39HqLyjWDarjtT1zdp7dc").uri("https://api.stripe.com/v1/" + path).get(e -> ResponseEntity.status(287).body(e.getBody()));
	}
}

@RestController
class LoanIssuanceController {

	private static final Logger log = LoggerFactory.getLogger(LoanIssuanceController.class);

	private final RestTemplate restTemplate;

	private final CircuitBreakerFactory factory;

	private final FraudClient fraudClient;

	private final ExternalClient externalClient;

	LoanIssuanceController(@LoadBalanced RestTemplate restTemplate, CircuitBreakerFactory factory, FraudClient fraudClient, ExternalClient externalClient) {
		this.restTemplate = restTemplate;
		this.factory = factory;
		this.fraudClient = fraudClient;
		this.externalClient = externalClient;
	}

	@PostMapping("/loan")
	@SuppressWarnings("unchecked")
	ResponseEntity frauds(@RequestBody LoanApplication loanApplication) {
		log.info("\n\nGot loan/ request\n\n");
//		List frauds =  factory.create("fraud").run(() -> this.restTemplate.getForObject("http://fraud-detection/frauds", List.class));
		List frauds = factory.create("fraud").run(fraudClient::frauds);
		if (frauds != null && !frauds.contains(loanApplication.getName())) {
			return ResponseEntity.status(HttpStatus.OK).body("LOAN_GRANTED");
		}
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body("LOAN_REJECTED");
	}

	@GetMapping("/circuit/fail")
	String fail() throws InterruptedException {
		log.info("\n\nGot failure example\n\n");
		Thread.sleep(100);
		return factory.create("fraud-fail").run(() -> this.restTemplate.getForObject("http://fraud-detection/fraudsasjkdhasjd", String.class));
	}

	@GetMapping("/circuit/fallback")
	String failFallback() throws InterruptedException {
		log.info("\n\nGot fallback example\n\n");
		Thread.sleep(100);
		return factory.create("fraud-fail").run(() -> this.restTemplate.getForObject("http://fraud-detection/fraudsasjkdhasjd", String.class), throwable -> "fallback");
	}

	@GetMapping("/external")
	String external() {
		return this.externalClient.google();
	}
}

@FeignClient("fraud-detection")
interface FraudClient {

	@GetMapping("/frauds")
	List<String> frauds();
}

@FeignClient(name = "google", url = "https://www.google.com/")
interface ExternalClient {

	@GetMapping("/")
	String google();

}

class LoanApplication {
	private String name;

	LoanApplication() {

	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}