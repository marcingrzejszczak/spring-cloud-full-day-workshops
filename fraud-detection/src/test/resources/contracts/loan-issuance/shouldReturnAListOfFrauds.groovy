import org.springframework.cloud.contract.spec.Contract

Contract.make {

	description("Returns a list of frauds")

	request {
		method(GET())
		url("/frauds")
	}

	response {
		status(OK())
		headers {
			contentType(applicationJson())
		}
		body(["olga", "oleg"])
	}

}