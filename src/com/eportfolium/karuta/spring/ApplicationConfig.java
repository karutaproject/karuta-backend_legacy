package com.eportfolium.karuta.spring;

import javax.sql.DataSource;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.eportfolium.karuta.data.utils.ConfigUtils;
import com.eportfolium.karuta.data.utils.SqlUtils;

@Configuration
public class ApplicationConfig {

	@Bean
	public ServletContextInitializer configInitializer() {
		return servletContext -> {
			try {
                // TODO: Untangle the need for servlet context and use env
                // Then have it loaded as regular bean
				ConfigUtils.init(servletContext);
			} catch (final Exception e) {
				e.printStackTrace();
			}
		};
	}

	@Bean
	public DataSource dataSource() {
		return SqlUtils.getDataSource();
	}
}
