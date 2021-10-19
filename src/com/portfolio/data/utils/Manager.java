package com.portfolio.data.utils;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Manager implements ServletContextListener {
    private final static Logger logger = LoggerFactory.getLogger(Manager.class);

    public void contextInitialized(ServletContextEvent event) {
        try {
            // Loading configKaruta.properties
            ConfigUtils.init(event.getServletContext());
        } catch (Exception e) {
            logger.error("Can't init application !", e);
        }
    }

    public void contextDestroyed(ServletContextEvent event) {
        // Releasing driver
        SqlUtils.close();
    }

}