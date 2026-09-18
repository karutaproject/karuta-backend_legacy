package com.eportfolium.karuta.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@ServletComponentScan("com.eportfolium.karuta.data.utils")
@SpringBootApplication
public class KarutaApplication {
	public static void main(String[] args) {
		SpringApplication.run(KarutaApplication.class, args);
	}
}
