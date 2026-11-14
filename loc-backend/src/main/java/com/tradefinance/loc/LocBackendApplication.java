package com.tradefinance.loc;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the LoC issuance backend.
 *
 * {@code @Deployment} makes the Camunda starter deploy every BPMN file in
 * src/main/resources/bpmn to Camunda when the application starts. Zeebe ignores
 * files that did not change, so restarting does not create new versions.
 */
@SpringBootApplication
@Deployment(resources = "classpath*:/bpmn/**/*.bpmn")
public class LocBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(LocBackendApplication.class, args);
	}

}
