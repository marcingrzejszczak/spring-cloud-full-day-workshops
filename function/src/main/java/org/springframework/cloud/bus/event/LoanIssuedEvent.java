package org.springframework.cloud.bus.event;

public class LoanIssuedEvent extends RemoteApplicationEvent {

    public LoanIssuedEvent(Object source, String originService) {
        super(source, originService);
    }

    LoanIssuedEvent(Object source, String originService, String destinationService) {
        super(source, originService, destinationService);
    }

    LoanIssuedEvent() {
    }
}