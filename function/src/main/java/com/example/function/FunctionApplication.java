package com.example.function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.bus.BusProperties;
import org.springframework.cloud.bus.event.LoanIssuedEvent;
import org.springframework.cloud.bus.event.RemoteApplicationEvent;
import org.springframework.cloud.bus.jackson.RemoteApplicationEventScan;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class FunctionApplication {

	public static void main(String[] args) {
		SpringApplication.run(FunctionApplication.class, args);
	}

}

@Service
@RefreshScope
class MyService {
	private final String property;

	MyService(@Value("${my.property:test}") String property) {
		this.property = property;
	}

	String myProperty() {
		return this.property;
	}
}

@RestController
class MyController {
	private final MyService myService;

	MyController(MyService myService) {
		this.myService = myService;
	}

	@GetMapping("/prop")
	String prop() {
		return this.myService.myProperty();
	}
}

class LoanRejectedEvent extends RemoteApplicationEvent {

	public LoanRejectedEvent(Object source, String originService) {
		super(source, originService);
	}

	LoanRejectedEvent(Object source, String originService, String destinationService) {
		super(source, originService, destinationService);
	}

	LoanRejectedEvent() {
	}
}

@RestController
class MyEventController {

	private final ApplicationEventPublisher publisher;

	private final BusProperties bus;

	MyEventController(ApplicationEventPublisher publisher, BusProperties bus) {
		this.publisher = publisher;
		this.bus = bus;
	}

	@PostMapping("/issued")
	void issued() {
		publisher.publishEvent(new LoanIssuedEvent(this, this.bus.getId()));
	}

	@PostMapping("/rejected")
	void rejected() {
		publisher.publishEvent(new LoanRejectedEvent(this, this.bus.getId()));
	}

	@EventListener(LoanIssuedEvent.class)
	public void gotLoanIssued() {
		System.out.println("\n\n GOT ISSUED!! \n\n");
	}

	@EventListener(LoanRejectedEvent.class)
	public void gotLoanRejected() {
		System.out.println("\n\n GOT REJECTED!! \n\n");
	}
}

@Configuration
@RemoteApplicationEventScan(basePackageClasses = {LoanRejectedEvent.class, LoanIssuedEvent.class})
class EventConfig {

}