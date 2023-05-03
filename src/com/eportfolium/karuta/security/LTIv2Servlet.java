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

package com.eportfolium.karuta.security;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthException;
import net.oauth.OAuthMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.tsugi.basiclti.Base64;
import org.tsugi.basiclti.BasicLTIConstants;
import org.tsugi.json.IMSJSONRequest;

public class LTIv2Servlet extends HttpServlet {

    private static final long serialVersionUID = -2442074091303775050L;

    private static final Logger logger = LoggerFactory.getLogger(LTIv2Servlet.class);

    private static final String LTI_MESSAGE_TYPE_TOOLPROXYREGISTRATIONREQUEST = "ToolProxyRegistrationRequest";
    private static final String LTI_MESSAGE_TYPE_TOOLPROXY_RE_REGISTRATIONREQUEST = "ToolProxyReregistrationRequest";
    private static final String REG_KEY = "reg_key";
    private static final String REG_PASSWORD = "reg_password";
    private static final String TC_PROFILE_URL = "tc_profile_url";

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    private void outTraceFormattedMessage(StringBuffer outTrace, String msg) {
        outTrace.append("\n").append(new Date()).append(" DEBUG ").append(msg);
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        StringBuffer outTrace = new StringBuffer();

        HttpSession session = request.getSession(true);
        String ppath = session.getServletContext().getRealPath("/");
        String outsideDir = ppath.substring(0, ppath.lastIndexOf("/")) + "_files/";

        ServletContext application = getServletConfig().getServletContext();

        //super.doPost(request, response);
        final String logFName = outsideDir + "logs/logLTI2.txt";
        outTraceFormattedMessage(outTrace, "doPost() - " + logFName);

        String toolProxyPath = outsideDir + "tool_proxy.txt";

        try {
//			wadbackend.WadUtilities.setApplicationAttributes(application, session);
            doRequest(request, response, session, application, toolProxyPath, outTrace);
        } catch (Exception e) {
            String ipAddress = request.getRemoteAddr();
            String uri = request.getRequestURI();
            logger.warn("General LTI2 Failure URI=" + uri + " IP=" + ipAddress);
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            doErrorJSON(request, response, null, "General failure", e);
        } finally {
            outTraceFormattedMessage(outTrace, "In finally()");
            if (LTIServletUtils.isTrace(application)) {
                outTraceFormattedMessage(outTrace, "In finally() with trace");
//				wadbackend.WadUtilities.appendlogfile(logFName, "POSTlti:" + outTrace.toString());
            }
        }
    }

    protected void doRequest(HttpServletRequest request, HttpServletResponse response, HttpSession session, ServletContext application, String toolProxyPath, StringBuffer outTrace)
            throws ServletException, IOException {

        outTraceFormattedMessage(outTrace, "getServiceURL=" + getServiceURL(request));

        String ipAddress = request.getRemoteAddr();
        outTraceFormattedMessage(outTrace, "LTI Service request from IP=" + ipAddress);

        final String uri = request.getRequestURI();
        final String[] parts = uri.split("/");
        if (parts.length < 4) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            doErrorJSON(request, response, null, "Incorrect url format", null);
            return;
        }

        Map<String, Object> payload = LTIServletUtils.processRequest(request, outTrace);
        String url = getServiceURL(request);

        String controller = parts[3];
        if ("register".equals(controller)) {
            payload.put("base_url", url);
            payload.put("launch_url", url + "register");
            doRegister(response, payload, application, toolProxyPath, outTrace);
            return;
        } else if ("launch".equals(controller)) {
            doLaunch(request, response, session, payload, application, outTrace);
            return;
        }

        // Check if json request if valid
        IMSJSONRequest jsonRequest = new IMSJSONRequest(request);
        if (jsonRequest.valid) {
            outTraceFormattedMessage(outTrace, jsonRequest.getPostBody());
        }

        response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        logger.warn("Unknown request=" + uri);
        doErrorJSON(request, response, null, "Unknown request=" + uri, null);
    }

    protected void doRegister(HttpServletResponse response, Map<String, Object> payload, ServletContext application, String toolProxyPath, StringBuffer outTrace) {
        final String launch_url = (String) payload.get("launch_url");
        response.setContentType("text/html");

        outTraceFormattedMessage(outTrace, "doRegister() - launch_url: " + launch_url);
        outTraceFormattedMessage(outTrace, "payload: " + payload);


        String key = null;
        String passwd = null;
        if (LTI_MESSAGE_TYPE_TOOLPROXYREGISTRATIONREQUEST.equals(
                payload.get(BasicLTIConstants.LTI_MESSAGE_TYPE))) {
            key = (String) payload.get(REG_KEY);
            passwd = (String) payload.get(REG_PASSWORD);
        } else if (LTI_MESSAGE_TYPE_TOOLPROXY_RE_REGISTRATIONREQUEST.equals(
                payload.get(BasicLTIConstants.LTI_MESSAGE_TYPE))) {
            key = (String) payload.get(LTIServletUtils.OAUTH_CONSUMER_KEY);
            final String configPrefix = "basiclti.provider." + key + ".";
            passwd = (String) application.getAttribute(configPrefix + "secret");

        } else {
            //TODO BOOM
            outTraceFormattedMessage(outTrace, "BOOM");
        }

        String returnUrl = (String) payload.get(BasicLTIConstants.LAUNCH_PRESENTATION_RETURN_URL);
        String tcProfileUrl = (String) payload.get(TC_PROFILE_URL);

        //Lookup tc profile
        if (tcProfileUrl != null && !"".equals(tcProfileUrl)) {
            InputStream is = null;
            try {
                URL url = new URL(tcProfileUrl);
                is = url.openStream();
                JSONParser parser = new JSONParser();
                JSONObject obj = (JSONObject) parser.parse(new InputStreamReader(is));
//			    is.close();
                outTraceFormattedMessage(outTrace, obj.toJSONString());
                JSONArray services = (JSONArray) obj.get("service_offered");
                String regUrl = null;
				for (Object value : services) {
					JSONObject service = (JSONObject) value;
					JSONArray formats = (JSONArray) service.get("format");
					if (formats.contains("application/vnd.ims.lti.v2.toolproxy+json")) {
						regUrl = (String) service.get("endpoint");
						outTraceFormattedMessage(outTrace, "RegEndpoint: " + regUrl);
					}
				}
                if (regUrl == null) {
                    //TODO BOOM
                    throw new RuntimeException("Need an endpoint");
                }

                JSONObject toolProxy = getToolProxy(toolProxyPath);
                //TODO do some replacement on stock values that need specifics from us here

                // Tweak the stock profile
                assert toolProxy != null;
                toolProxy.put("tool_consumer_profile", tcProfileUrl);
//LTI2Constants.
//				BasicLTIConstants.

//				// Re-register
                JSONObject toolProfile = (JSONObject) toolProxy.get("tool_profile");
                JSONArray messages = (JSONArray) toolProfile.get("message");
                JSONObject message = (JSONObject) messages.get(0);
                message.put("path", launch_url);
                String baseUrl = (String) payload.get("base_url");

                JSONObject pi = (JSONObject) toolProfile.get("product_instance");
                JSONObject pInfo = (JSONObject) pi.get("product_info");
                JSONObject pFamily = (JSONObject) pInfo.get("product_family");
                JSONObject vendor = (JSONObject) pFamily.get("vendor");
                vendor.put("website", baseUrl);
//				vendor.put("timestamp", new Date().toString());
//				$tp_profile->tool_profile->product_instance->product_info->product_family->vendor->website = $cur_base;
//				$tp_profile->tool_profile->product_instance->product_info->product_family->vendor->timestamp = "2013-07-13T09:08:16-04:00";
//
//				// I want this *not* to be unique per instance
//				$tp_profile->tool_profile->product_instance->guid = "urn:sakaiproject:unit-test";
//
//				$tp_profile->tool_profile->product_instance->service_provider->guid = "http://www.sakaiproject.org/";
//
//				// Launch Request
//				$tp_profile->tool_profile->resource_handler[0]->message[0]->path = "tool.php";
//				$tp_profile->tool_profile->resource_handler[0]->resource_type->code = "sakai-api-test-01";

//				$tp_profile->tool_profile->base_url_choice[0]->secure_base_url = $cur_base;
//				$tp_profile->tool_profile->base_url_choice[0]->default_base_url = $cur_base;
                JSONObject choice = (JSONObject) ((JSONArray) toolProfile.get("base_url_choice")).get(0);
                choice.put("secure_base_url", baseUrl);
                choice.put("default_base_url", baseUrl);

                JSONObject secContract = (JSONObject) toolProxy.get("security_contract");
                secContract.put("shared_secret", passwd);
                JSONArray toolServices = (JSONArray) secContract.get("tool_service");
                JSONObject service = (JSONObject) toolServices.get(0);
                service.put("service", regUrl);


                outTraceFormattedMessage(outTrace, "ToolProxyJSON: " + toolProxy.toJSONString());

                /// From the Implementation Guid Version 2.0 Final (http://www.imsglobal.org/lti/ltiv2p0/ltiIMGv2p0.html)
                /// Section 10.1
                /// Get data
                JSONObject dataService = getData(tcProfileUrl);

                /// find endpoint with format: application/vnd.ims.lti.v2.toolproxy+json WITH POST action
                JSONArray offered = (JSONArray) dataService.get("service_offered");
                String registerAddress = "";
				for (Object o : offered) {
					JSONObject offer = (JSONObject) o;
					JSONArray offerFormat = (JSONArray) offer.get("format");
					String format = (String) offerFormat.get(0);
					JSONArray offerAction = (JSONArray) offer.get("action");
					String action = (String) offerAction.get(0);
					if ("application/vnd.ims.lti.v2.toolproxy+json".equals(format) && "POST".equals(action)) {
						registerAddress = (String) offer.get("endpoint");
						break;
					}
				}

                /// FIXME: Sakai return server name as "localhost", could be my configuration
                String[] serverAddr = tcProfileUrl.split("/");
                String addr = serverAddr[2];
                registerAddress = registerAddress.substring(registerAddress.indexOf("/", 8));
                registerAddress = "http://" + addr + registerAddress;

                /// Send POST to specified URL as signed request with given values
                int responseCode = postData(registerAddress, toolProxy.toJSONString(), key, passwd);
                if (responseCode != HttpServletResponse.SC_CREATED) {
                    //TODO BOOM!
                    throw new RuntimeException("Bad response code.  Got " + responseCode + " but expected " + HttpServletResponse.SC_CREATED);
                }

            } catch (IOException | ParseException e) {
                // TODO Auto-generated catch block
                logger.error("Exception ", e);
            } finally {
                try {
                    if (is != null)
                        is.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    logger.error("IOException ", e);
                }
            }

        }

        String output = "<a href='" + returnUrl + "'>Continue to launch presentation url</a>";

        try {
            PrintWriter out = response.getWriter();
            out.println(output);
        } catch (Exception e) {
            logger.error("Exception ", e);
        }
    }

    private int postData(String urlStr, String jsonData, String oauth_consumer_key, String oauth_consumer_secret) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/vnd.ims.lti.v2.toolproxy+json");
        connection.setRequestProperty("Content-Length", String.valueOf(jsonData.length()));

        Map<String, String> postProp = new HashMap<>();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");

            md.update(jsonData.getBytes());
            byte[] output = Base64.encode(md.digest());
            String hash = new String(output);

            postProp.put("oauth_body_hash", hash);
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            logger.error("Exception ", e);
        }

        OAuthMessage oam = new OAuthMessage(OAuthMessage.POST, urlStr, postProp.entrySet());

        OAuthConsumer cons = new OAuthConsumer("about:blank", oauth_consumer_key,
                oauth_consumer_secret, null);
        OAuthAccessor acc = new OAuthAccessor(cons);
        try {
            oam.addRequiredParameters(acc);
            connection.setRequestProperty("Authorization", oam.getAuthorizationHeader(null));
            oam.sign(acc);
        } catch (OAuthException | IOException | URISyntaxException e) {
            logger.error("OAUTH Exception ", e);
            throw new Error(e);
        }

		// Write data
        OutputStream os = connection.getOutputStream();
        os.write(jsonData.getBytes());

        // Read response
        int responseCode = connection.getResponseCode();

        // Close streams
        os.close();

        return responseCode;

    }

    private JSONObject getData(String urlStr) throws IOException, ParseException {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "application/vnd.ims.lti.v2.toolproxy+json");

        connection.connect();

        BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null)
            sb.append(line);

        return (JSONObject) new JSONParser().parse(sb.toString());
    }

    @SuppressWarnings("unused")
    protected void doLaunch(HttpServletRequest request, HttpServletResponse response, HttpSession session,
                            Map<String, Object> payload, ServletContext application, StringBuffer outTrace) throws ServletException, IOException {
        //TODO Figure out how to validate v1 and v2 params
//		LTIServletUtils.validateParams(payload, application, outTrace);
        LTIServletUtils.oauthValidate(request, payload, application);
        outTraceFormattedMessage(outTrace, "doLaunch()");
        LTIServletUtils.handleLaunch(payload, application, response, session, outTrace);
    }

    private JSONObject getToolProxy(String filePath) {
        try {
            File f = new File(filePath);
            if (!f.exists()) {
                f.mkdirs();
                f.createNewFile();
            }
            FileInputStream fis = new FileInputStream(filePath);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
            return (JSONObject) new JSONParser().parse(br);
        } catch (IOException | ParseException e) {
            // TODO Auto-generated catch block
            logger.error("Exception ", e);
        }
		return null;
    }

    private String getServiceURL(HttpServletRequest request) {
        String scheme = request.getScheme();             // http
        String serverName = request.getServerName();     // localhost
        int serverPort = request.getServerPort();        // 80
        String contextPath = request.getContextPath();   // /imsblis
        String servletPath = request.getServletPath();   // /ltitest
        return scheme + "://" + serverName + ":" + serverPort + contextPath + servletPath + "/";
    }


    /* IMS JSON version of Errors */
    private void doErrorJSON(HttpServletRequest request, HttpServletResponse response,
                             IMSJSONRequest json, String message, Exception e)
            throws java.io.IOException {
        if (e != null) {
            logger.error(e.getLocalizedMessage(), e);
        }
        logger.info(message);
		logger.info(IMSJSONRequest.doErrorJSON(request, response, json, message, e));
    }

    @Override
    public void destroy() {
    }

}