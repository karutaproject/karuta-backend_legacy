package com.eportfolium.karuta.data.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;

public class Manager implements ServletContextListener {
	private final static Logger logger = LoggerFactory.getLogger(Manager.class);

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
		} catch (final Exception e) {
			logger.error("Can't init application !", e);
		}
	}

}