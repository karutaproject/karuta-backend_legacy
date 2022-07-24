/* =======================================================
	Copyright 2014 - ePortfolium - Licensed under the
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

package com.portfolio.data.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigUtils {
    public static final String KARUTA_ENV_HOME = "KARUTA_HOME";
    public static final String KARUTA_PROP_HOME = "karuta.home";

    private static final Logger logger = LoggerFactory.getLogger(ConfigUtils.class);

    // Singleton pattern
    private static ConfigUtils INSTANCE;

    private boolean hasLoaded = false;
    private final Properties properties = new Properties();
    private String filePath;
    private String configPath;
    private String karutaHome;
    private String servletName;

    private ConfigUtils(final ServletContext context) throws Exception {
        if (hasLoaded) return;
        try {
            this.loadConfigDirectory(context);

            filePath = configPath + "configKaruta.properties";
            // loading properties
            FileInputStream fileProps = new FileInputStream(filePath);
            properties.load(fileProps);
            fileProps.close();

            hasLoaded = true;
            logger.info("Configuration file loaded: {}", filePath);
            logger.trace("Loaded properties: {}", properties);
        } catch (Exception e) {
            logger.error("Can't load file :" + filePath, e);
            throw e;
        }
    }

    public static ConfigUtils getInstance() {
        if (INSTANCE == null) {
            throw new AssertionError("The init wasn't done !");
        }
        return INSTANCE;
    }


    public synchronized static ConfigUtils init(final ServletContext context) throws Exception {
        if (INSTANCE == null) {
            INSTANCE = new ConfigUtils(context);
        }

        return INSTANCE;
    }

    private void loadConfigDirectory(final ServletContext context) throws IOException, InternalError {
        final String configEnvDir = System.getenv(KARUTA_ENV_HOME);
        final String configPropDir = System.getProperty(KARUTA_PROP_HOME);
        // The jvm property override the environment property if set
        final String configDir = (configPropDir != null && !configPropDir.trim().isEmpty()) ? configPropDir : configEnvDir;
        servletName = context.getContextPath();
        if (configDir != null && !configDir.trim().isEmpty()) {
            final File base = new File(configDir.trim());
            if (base.exists() && base.isDirectory() && base.canWrite()) {
                setConfigFolder(base);
            } else {
                logger.error("The environment variable '" + KARUTA_ENV_HOME + "' '" + configEnvDir
                        + "' or the jvm property '" + KARUTA_PROP_HOME + "' '" + configPropDir
                        + "' doesn't exist or isn't writable. Please provide a writable directory path !");
                throw new IllegalArgumentException("The environment variable '" + KARUTA_ENV_HOME + "' '" + configEnvDir
                        + "' or the jvm property '" + KARUTA_PROP_HOME + "' '" + configPropDir
                        + "' doesn't exist or isn't writable. Please provide a writable directory path !");
            }
        } else {
            final String defaultDir = System.getProperty("catalina.base");
            logger.warn("The environment variable '" + KARUTA_ENV_HOME
                    + "' or the jvm property '" + KARUTA_PROP_HOME + "' wasn't set."
                    + " Use theses variables to set a custom configuration path outside of tomcat installation."
                    + " Fallback on default folder '" + defaultDir + "'.");
            final File base = new File(defaultDir);
            if (base.exists() && base.isDirectory() && base.canWrite()) {
                setConfigFolder(base);
            } else {
                logger.error("The folder '" + defaultDir
                        + "' provided from 'catalina.base' doesn't exist or isn't writable. It's required for configuration files !");
                throw new IllegalArgumentException("The folder '" + defaultDir
                        + "' provided from 'catalina.base' doesn't exist or isn't writable. It's required for configuration files !");
            }

        }
    }

    private void setConfigFolder(final File base) throws IOException {
        try {
            karutaHome = base.getCanonicalPath();
            configPath = karutaHome + servletName + "_config" + File.separatorChar;
            logger.info("Karuta-backend Servlet configpath @ " + configPath);
        } catch (IOException e) {
            logger.error("The configuration directory '" + karutaHome + "' wasn't defined", e);
            throw e;
        }
    }

    public String getRequiredProperty(final String key) throws IllegalStateException {
        final String value = properties.getProperty(key);
        if (value == null) {
            logger.error("Required property key '" + key + "' not found");
            throw new IllegalStateException("Required key '" + key + "' not found");
        }
        return value;
    }

    public String getProperty(final String key) {
        return properties.getProperty(key);
    }

    public String getProperty(final String key, final String defaultValue) {
        final String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public String getConfigPath() {
        return configPath;
    }

    public String getKarutaHome() {
        return karutaHome;
    }

    public String getServletName() {
        return servletName;
    }
}