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

package com.eportfolium.karuta.data.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringJoiner;
import java.util.jar.Manifest;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicHeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.MediaType;

public class ConfigUtils {
	public class BuildInfo {
		protected String version;
		protected String buildTime;
		protected String builtBy;

		public String getBuildTime() {
			return buildTime;
		}

		public String getBuiltBy() {
			return builtBy;
		}

		public String getVersion() {
			return version;
		}

		@Override
		public String toString() {
			return new StringJoiner(", ", BuildInfo.class.getSimpleName() + "[", "]").add("version='" + version + "'")
					.add("buildTime='" + buildTime + "'").add("builtBy='" + builtBy + "'").toString();
		}
	}

	public static final String KARUTA_ENV_HOME = "KARUTA_HOME";

	public static final String KARUTA_PROP_HOME = "karuta.home";

	private static final Logger logger = LogManager.getLogger(ConfigUtils.class);

	// Singleton pattern
	private static ConfigUtils INSTANCE;

	private boolean hasLoaded = false;

	private final Properties properties = new Properties();

	private String filePath;
	private String configPath;
	private String karutaHome;

	private String servletName;

	private BuildInfo buildInfo;

	private BuildInfo fileServerBuildinfo;

	private ConfigUtils(final ServletContext context) throws Exception {
		if (hasLoaded) {
			return;
		}
		try {
			this.loadConfigDirectory(context);

			filePath = configPath + "configKaruta.properties";
			// loading properties
			final var fileProps = new FileInputStream(filePath);
			properties.load(fileProps);
			fileProps.close();

			hasLoaded = true;
			logger.info("Configuration file loaded: {}", filePath);
			logger.trace("Loaded properties: {}", properties);
			loadBuildedInfo(context);
		} catch (final Exception e) {
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

	public boolean getBooleanProperty(final String key) {
		final var value = properties.getProperty(key);
		if (value == null) {
			return false;
		}
		return "Y".equals(value.toUpperCase());
	}

	public BuildInfo getBuildInfo() {
		return buildInfo;
	}

	public String getConfigPath() {
		return configPath;
	}

	public BuildInfo getFileServerBuildinfo() {
		if (fileServerBuildinfo == null) {
			this.loadFileserverBuildedInfo();
		}
		return fileServerBuildinfo;
	}

	public String getKarutaHome() {
		return karutaHome;
	}

	public String getProperty(final String key) {
		return properties.getProperty(key);
	}

	public String getProperty(final String key, final String defaultValue) {
		final var value = properties.getProperty(key);
		if (value == null) {
			return defaultValue;
		}
		return value;
	}

	public String getRequiredProperty(final String key) throws IllegalStateException {
		final var value = properties.getProperty(key);
		if (value == null) {
			logger.error("Required property key '" + key + "' not found");
			throw new IllegalStateException("Required key '" + key + "' not found");
		}
		return value;
	}

	public String getServletName() {
		return servletName;
	}

	private void loadBuildedInfo(final ServletContext context) {
		final var inputStream = context.getResourceAsStream("/META-INF/MANIFEST.MF");
		Manifest manifest = null;
		try {
			manifest = new Manifest(inputStream);
		} catch (final IOException e) {
			logger.error("The war have a build problem in generating Manifest.mf file !");
			return;
		}
		final var attr = manifest.getMainAttributes();

		final var bi = new BuildInfo();
		bi.version = attr.getValue("Implementation-Version");
		bi.buildTime = attr.getValue("Build-Time");
		bi.builtBy = attr.getValue("Built-By");
		this.buildInfo = bi;
		logger.info("Loaded from META-INF/MANIFEST.MF build information: {}", this.buildInfo);
	}

	private void loadConfigDirectory(final ServletContext context) throws IOException, InternalError {
		final var configEnvDir = System.getenv(KARUTA_ENV_HOME);
		final var configPropDir = System.getProperty(KARUTA_PROP_HOME);
		// The jvm property override the environment property if set
		final var configDir = (configPropDir != null && !configPropDir.trim().isEmpty()) ? configPropDir : configEnvDir;
		servletName = context.getContextPath();
		if (configDir != null && !configDir.trim().isEmpty()) {
			final var base = new File(configDir.trim());
			if (!base.exists() || !base.isDirectory() || !base.canWrite()) {
				logger.error("The environment variable '" +
						KARUTA_ENV_HOME +
						"' '" +
						configEnvDir +
						"' or the jvm property '" +
						KARUTA_PROP_HOME +
						"' '" +
						configPropDir +
						"' doesn't exist or isn't writable. Please provide a writable directory path !");
				throw new IllegalArgumentException("The environment variable '" +
						KARUTA_ENV_HOME +
						"' '" +
						configEnvDir +
						"' or the jvm property '" +
						KARUTA_PROP_HOME +
						"' '" +
						configPropDir +
						"' doesn't exist or isn't writable. Please provide a writable directory path !");
			}
			setConfigFolder(base);
		} else {
			final var defaultDir = System.getProperty("catalina.base");
			logger.warn("The environment variable '" +
					KARUTA_ENV_HOME +
					"' or the jvm property '" +
					KARUTA_PROP_HOME +
					"' wasn't set." +
					" Use theses variables to set a custom configuration path outside of tomcat installation." +
					" Fallback on default folder '" +
					defaultDir +
					"'.");
			final var base = new File(defaultDir);
			if (!base.exists() || !base.isDirectory() || !base.canWrite()) {
				logger.error("The folder '" +
						defaultDir +
						"' provided from 'catalina.base' doesn't exist or isn't writable. It's required for configuration files !");
				throw new IllegalArgumentException("The folder '" +
						defaultDir +
						"' provided from 'catalina.base' doesn't exist or isn't writable. It's required for configuration files !");
			}
			setConfigFolder(base);

		}
	}

	private void loadFileserverBuildedInfo() {
		var url = ConfigUtils.getInstance().getProperty("fileserver");
		url = url.endsWith("/") ? url + "rest/api/version" : url + "/rest/api/version";

		final Set<Header> headers = new HashSet<>();
		headers.add(new BasicHeader("Content-Type", MediaType.APPLICATION_JSON));
		headers.add(new BasicHeader("Accept", MediaType.APPLICATION_JSON));
		headers.add(new BasicHeader("Accept-Charset", StandardCharsets.UTF_8.name()));
		final HttpResponse response = HttpClientUtils.goGet(headers, url);
		if (response != null) {
			final var httpentity = response.getEntity();
			try {
				final var inputStream = httpentity.getContent();

				final var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), 8);
				final var sb = new StringBuilder();
				String line = null;

				while ((line = reader.readLine()) != null) {
					sb.append(line + "\n");
				}
				inputStream.close();

				final var gson = new Gson();
				fileServerBuildinfo = gson.fromJson(sb.toString(), BuildInfo.class);

			} catch (final IOException e) {
				logger.error("Can't get Fileserver Version, the request to '{}' provided this error ", url, e);
			}
		}

		logger.info("Loaded from '{}' build information: {}", url, this.fileServerBuildinfo);
	}

	private void setConfigFolder(final File base) throws IOException {
		try {
			karutaHome = base.getCanonicalPath();
			configPath = karutaHome + servletName + "_config" + File.separatorChar;
			logger.info("Karuta-backend Servlet configpath @ " + configPath);
		} catch (final IOException e) {
			logger.error("The configuration directory '" + karutaHome + "' wasn't defined", e);
			throw e;
		}
	}
}