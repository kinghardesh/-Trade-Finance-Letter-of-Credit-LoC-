package com.tradefinance.loc;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the LoC issuance backend.
 *
 * {@code @Deployment} makes the Camunda starter deploy every BPMN file and every
 * Camunda Form (.form, linked to the user tasks) in src/main/resources/bpmn when the
 * application starts. Zeebe ignores files that did not change, so restarting does
 * not create new versions.
 */
@SpringBootApplication
@Deployment(resources = {"classpath*:/bpmn/**/*.bpmn", "classpath*:/bpmn/**/*.form"})
public class LocBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(LocBackendApplication.class, args);
	}

}
