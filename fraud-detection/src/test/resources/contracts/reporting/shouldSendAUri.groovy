import org.springframework.cloud.contract.spec.Contract

Contract.make {
	description("Sends out a message with URI")
	label("trigger_fraud_uri")
	input {
		triggeredBy("frauds()")
	}
	outputMessage {
		sentTo("events")
		headers {
			messagingContentType(applicationJson())
		}
		body([
				applicationName: "fraud-detection",
				timestamp: $(anyPositiveInt()),
				uri: $(anyUrl())
		])
	}
}