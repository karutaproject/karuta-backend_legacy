package com.eportfolium.karuta.data.utils;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eportfolium.karuta.data.attachment.EmploiStoreService;

public class Manager implements ServletContextListener {
	private final static Logger logger = LoggerFactory.getLogger(Manager.class);

	public static final String ROME_DOMAIN = "ROMEdomain";
	public static final String ROME_SCOPE = "ROMEscope";

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		// Releasing driver
		SqlUtils.close();
	}

	@Override
	public void contextInitialized(ServletContextEvent event) {
		try {
			// Loading configKaruta.properties
			ConfigUtils.init(event.getServletContext());

			final var domains = ConfigUtils.getInstance().getProperty(ROME_DOMAIN);
			final var scopes = ConfigUtils.getInstance().getProperty(ROME_SCOPE);
			if (domains != null && scopes != null) {
				final var domainSplit = domains.split(" ");
				final var scopeSplit = scopes.split(" ");
				if (domainSplit.length == scopeSplit.length) {
					for (var i = 0; i < domainSplit.length; ++i) {
						final var domain = domainSplit[i];
						final var scope = scopeSplit[i];
						final var context = event.getServletContext();
						final var servlet = context.addServlet("ROME_" + domain, EmploiStoreService.class);
						if (servlet != null) {
							servlet.setInitParameter("domain", domain);
							servlet.setInitParameter("scope", scope);
							servlet.addMapping("/rome/" + domain + "/*");
						} else {
							logger.error("Failed to register servlet " + domain);
						}
					}
				}
			}

		} catch (final Exception e) {
			logger.error("Can't init application !", e);
		}
	}

}