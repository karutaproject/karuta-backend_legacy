package com.eportfolium.karuta.spring;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JerseyConfig extends ResourceConfig {

	public JerseyConfig() {
		packages("com.eportfolium.karuta.rest");
	}
}
