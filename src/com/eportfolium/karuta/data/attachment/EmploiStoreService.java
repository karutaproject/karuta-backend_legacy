/* =======================================================
	Copyright 2019 - ePortfolium - Licensed under the
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

package com.eportfolium.karuta.data.attachment;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.eportfolium.karuta.data.utils.ConfigUtils;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;

public class EmploiStoreService extends HttpServlet {
	public class CacheCleanup {
		Duration expiration;
		ScheduledExecutorService scheduler;

		public CacheCleanup(Duration expiration, long cleanupIntervalMinutes) {
			logger.info("Starting cache cleanup thread");
			this.expiration = expiration;
			this.scheduler = Executors.newScheduledThreadPool(1);
			this.scheduler.scheduleAtFixedRate(this::cleanupCache, cleanupIntervalMinutes, cleanupIntervalMinutes,
					TimeUnit.MINUTES);
		}

		public void cleanupCache() {
			final var now = LocalDateTime.now();
			cache.entrySet().removeIf(entry -> {
				final var v = entry.getValue().getLastModified();
				return v != null && now.isAfter(v.plus(expiration));
			});
		}

		public void shutdown() {
			logger.info("Shutting down cache cleanup thread");
			scheduler.shutdown();
			try {
				if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
					scheduler.shutdownNow();
				}
			} catch (final InterruptedException e) {
				scheduler.shutdownNow();
				Thread.currentThread().interrupt();
			}

		}
	}

	/// Service doesn't implement Last-Modified, keep data for 5 min
	public class CacheEntry {
		private final String data;
		private final LocalDateTime lastModified;

		public CacheEntry(String data, LocalDateTime lastModified) {
			this.data = data;
			this.lastModified = lastModified;
		}

		public String getData() {
			return data;
		}

		public LocalDateTime getLastModified() {
			return lastModified;
		}
	}

	private static final long serialVersionUID = -5389232495090560087L;

	private static final Logger logger = LogManager.getLogger(EmploiStoreService.class);
	public static final Pattern PATTERN_TOKEN = Pattern.compile("access_token\":\"([^\"]*)");
	public static final Pattern PATTERN_EXPIRES = Pattern.compile("expires_in\":([^}]*)");
	public static final String ROME_SERVICE_URL = "ROMEServiceURL";
	public static final String ROME_CLIENT_ID = "ROMEclientid";
	public static final String ROME_CLIENT_SECRET = "ROMEclientsecret";
	public static final String ROME_DOMAIN = "ROMEdomain";
	public static final String ROME_SCOPE = "ROMEscope";
	public static final String ROME_REPO_URL = "ROMERepoURL";

	private String serviceURL;
	private String scopestr;
	private String repoURL;

	private final ReadWriteLock tokenLock = new ReentrantReadWriteLock();
	private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
	private final AtomicReference<String> tokenRef = new AtomicReference<>();
	private LocalDateTime lastlogin;

	private final int cacheEntryTTL = 5; // Minutes after cache entry can be renewed
	private final int cacheCleanupDelay = 5; // Keep it this minute more after cache expiring
	private final int cacheCleanupInterval = 60; // Minutes between running cache cleanup

	// Cache cleanup
	CacheCleanup cacheCleanup = null;

	/// Java 11 can't close this, keep it around until code is at Java 21 and use try-with-resource
	final private HttpClient client = HttpClient.newBuilder().build();

	@Override
	public void destroy() {
		super.destroy();
		if (cacheCleanup != null) {
			cacheCleanup.shutdown();
		}
	}

	public String fetchData(String queryURL) throws URISyntaxException, IOException, InterruptedException {
		CacheEntry entry = null;
		if (cache.containsKey(queryURL)) {
			entry = cache.get(queryURL);
			final var lastModified = entry.getLastModified();
			if (!LocalDateTime.now().isAfter(lastModified)) {
				return entry.getData();
			}
			// Need some user agent, otherwise return 409
			//			getrequest = HttpRequest.newBuilder().uri(new URI(queryURL))
			//					.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRef.get())
			//					.header(HttpHeaders.USER_AGENT, "Error 409 without user-agent")
			//					.header(HttpHeaders.LAST_MODIFIED, lastModified).GET().build();
		} else {
			logger.error("Not in cache");
		}

		final var getrequest = HttpRequest.newBuilder().uri(new URI(queryURL))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenRef.get())
				.header(HttpHeaders.USER_AGENT, "Error 409 without user-agent").GET().build();
		final HttpResponse<String> getresponse = client.send(getrequest, BodyHandlers.ofString(StandardCharsets.UTF_8));

		final var code = getresponse.statusCode();
		//		final var lastModified = getresponse.headers().firstValue(HttpHeaders.LAST_MODIFIED).orElse(null);
		//		logger.error("Last modified: " + lastModified);

		if (code == HttpURLConnection.HTTP_OK) {
			final var body = getresponse.body();
			// Will re-request data if it's older than _cacheEntryTTL_ minutes
			// Might have to put cache delay in property
			entry = new CacheEntry(body, LocalDateTime.now().plus(cacheEntryTTL, ChronoUnit.MINUTES));
			cache.put(queryURL, entry);
		} else if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
			// Same content, just cached value
		}
		if (entry != null) {
			return entry.getData();
		}

		return null;
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			ConfigUtils.init(getServletContext());

			final var apiDomain = config.getInitParameter("domain");
			scopestr = config.getInitParameter("scope");
			repoURL = ConfigUtils.getInstance().getRequiredProperty(ROME_REPO_URL) + "/" + apiDomain;
			serviceURL = ConfigUtils.getInstance().getRequiredProperty(ROME_SERVICE_URL);

			lastlogin = LocalDateTime.now();

			cacheCleanup = new CacheCleanup(Duration.ofMinutes(cacheCleanupDelay), cacheCleanupInterval);
		} catch (final Exception e) {
			logger.error("Can't init servlet:", e);
			throw new ServletException(e);
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		/// Only people from our system can query
		final var session = request.getSession(false);
		if (session == null || session.getAttribute("uid") == null) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			try (var out = response.getWriter();) {
				out.write("403");
			} catch (final IOException e) {
				logger.error("Intercepted error", e);
				//TODO something is missing
			}
			return;
		}

		refreshTokenIfNeeded();

		///// Send wanted query
		var pathinfo = request.getPathInfo();
		var query = request.getQueryString();
		if ("/".equals(pathinfo) || pathinfo == null) {
			pathinfo = "";
		}
		if (query == null) {
			query = "";
		} else {
			query = "?" + query;
		}

		final var queryURL = String.format("%s%s%s", repoURL, pathinfo, query);
		logger.info("Query to: {}", queryURL);

		try {
			final var data = fetchData(queryURL);
			response.setHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");
			try (final var writer = response.getWriter();) {
				writer.write(data);
			}
		} catch (final Exception e) {
			logger.error(e.getMessage());
		}
		response.setStatus(HttpServletResponse.SC_OK);
	}

	private int doLogin() {
		// Only keep id and secret for login
		final var clientid = ConfigUtils.getInstance().getRequiredProperty(ROME_CLIENT_ID);
		final var clientsecret = ConfigUtils.getInstance().getRequiredProperty(ROME_CLIENT_SECRET);

		try {
			final var body = String.format("grant_type=%s&client_id=%s&client_secret=%s&scope=%s", "client_credentials",
					clientid, clientsecret, scopestr);

			final var request = HttpRequest.newBuilder().uri(new URI(serviceURL))
					.headers(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body)).build();

			logger.info("body: {}", body);

			final HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

			/// Read answer
			final var code = response.statusCode();
			final var msg = response.body();
			if (code != HttpURLConnection.HTTP_OK) {
				logger.error("Couldn't log: {}", msg);
				return -1;
			}
			logger.info("Code: ({}) msg: {}", code, msg);

			/// Can't be bothered to parse json
			final var pmatcher = PATTERN_TOKEN.matcher(msg.toString());
			if (pmatcher.find()) {
				tokenRef.set(pmatcher.group(1));
			}

			final var pmatcherExpire = PATTERN_EXPIRES.matcher(msg.toString());
			var expiresSec = 0;
			if (pmatcherExpire.find()) {
				// Remove some 2 sec in the off chance token expire just as it is used
				expiresSec = Integer.parseInt(pmatcherExpire.group(1)) - 2;
			}

			lastlogin = LocalDateTime.now().plus(expiresSec, ChronoUnit.SECONDS);
			logger.info("{} -- Current token: {} (expire in {}s)", scopestr, tokenRef.get(), expiresSec);
		} catch (final Exception e) {
			logger.error("Intercepted error", e);
		}

		return 0;
	}

	/// Ensure token is good to go
	private void refreshTokenIfNeeded() {
		//// Login to service if token expired
		tokenLock.readLock().lock();
		try {
			if (LocalDateTime.now().isAfter(lastlogin)) {
				// Need to refresh
				tokenLock.readLock().unlock();
				if (tokenLock.writeLock().tryLock(5, TimeUnit.SECONDS)) {
					try {
						if (LocalDateTime.now().isAfter(lastlogin)) {
							logger.error("Refresh login");
							doLogin();
						}
					} finally {
						tokenLock.writeLock().unlock();
					}
				} else {
					Thread.sleep(100);
					refreshTokenIfNeeded(); // Recursive call to retry
				}
			} else {
				tokenLock.readLock().unlock();
			}

		} catch (final Exception e) {
		} finally {
		}
	}
}
