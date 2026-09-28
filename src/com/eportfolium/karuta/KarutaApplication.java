package com.eportfolium.karuta;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KarutaApplication {
	public static void main(String[] args) {
		SpringApplication.run(KarutaApplication.class, args);
	}
}
