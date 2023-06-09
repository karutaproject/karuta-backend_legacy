package com.eportfolium.karuta.data.utils;

import java.io.IOException;
import java.util.Set;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientUtils.class);

    private static CloseableHttpClient httpclient;

    public static CloseableHttpResponse goGet(final Set<Header> headers, final String url) {
        try {
            if (httpclient == null) {
                httpclient = HttpClients.createSystem();
            }

            /// Fetch page
            HttpGet get = new HttpGet(url);
            for (Header header : headers) {
                get.addHeader(header);
            }


            final CloseableHttpResponse response = httpclient.execute(get);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                logger.error("Method failed: {} on {} and with headers {}", response.getStatusLine(), url, headers);
                return null;
            }

            return response;
        } catch (IOException e) {
            logger.error("Can't do Request on {} and with headers {}", url, headers, e);
        }
        return null;
    }
}