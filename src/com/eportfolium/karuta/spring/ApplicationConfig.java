/* =======================================================
	Copyright 2026 - ePortfolium - Licensed under the
	Educational Community License, Version 2.0 (the "License"); you may
	not use this file except in compliance with the License. You may
	obtain a copy of the License at

	http://www.osedu.org/licenses/ECL-2.0

	Unless required by applicable law or agreed to in writing,
	software distributed under the License is distributed on an "AS IS"
	BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
	or implied. See the License for the specific language governing
	permissions and limitations under the License.
   ======================================================= */

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
