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

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.eportfolium.karuta.data.attachment.CNAMBDO;
import com.eportfolium.karuta.data.attachment.CompareServlet;
import com.eportfolium.karuta.data.attachment.ConvertCSV;
import com.eportfolium.karuta.data.attachment.DirectURLService;
import com.eportfolium.karuta.data.attachment.ExportHTMLService;
import com.eportfolium.karuta.data.attachment.FileServlet;
import com.eportfolium.karuta.data.attachment.LoggingService;
import com.eportfolium.karuta.data.attachment.MailService;
import com.eportfolium.karuta.data.attachment.MessageService;
import com.eportfolium.karuta.data.attachment.PingService;
import com.eportfolium.karuta.data.attachment.RegisterService;
import com.eportfolium.karuta.data.attachment.ReportHelper;
import com.eportfolium.karuta.data.attachment.ReportService;
import com.eportfolium.karuta.data.attachment.XSLService;
import com.eportfolium.karuta.security.LTIServlet;
import com.eportfolium.karuta.security.LTIServletImport;
import com.eportfolium.karuta.security.OAuth2;
import com.eportfolium.karuta.security.ShibeServlet;

@Configuration
public class WebConfig {
	@Bean
	public ServletRegistrationBean<CNAMBDO> cnamServlet() {
		return new ServletRegistrationBean<>(new CNAMBDO(), "/cnam/*");
	}

	@Bean
	public ServletRegistrationBean<CompareServlet> compareServlet() {
		return new ServletRegistrationBean<>(new CompareServlet(), "/compare/*");
	}

	@Bean
	public ServletRegistrationBean<ConvertCSV> csvServlet() {
		return new ServletRegistrationBean<>(new ConvertCSV(), "/csv");
	}

	@Bean
	public ServletRegistrationBean<DirectURLService> directURLServlet() {
		return new ServletRegistrationBean<>(new DirectURLService(), "/direct");
	}

	@Bean
	public ServletRegistrationBean<ExportHTMLService> exportHTMLServlet() {
		return new ServletRegistrationBean<>(new ExportHTMLService(), "/oauth2");
	}

	@Bean
	public ServletRegistrationBean<FileServlet> fileServlet() {
		return new ServletRegistrationBean<>(new FileServlet(), "/resources/resource/file/*");
	}

	@Bean
	public ServletRegistrationBean<LoggingService> loggingServlet() {
		return new ServletRegistrationBean<>(new LoggingService(), "/logging");
	}

	@Bean
	public ServletRegistrationBean<LTIServletImport> ltiImportServlet() {
		return new ServletRegistrationBean<>(new LTIServletImport(), "/retrieve");
	}

	@Bean
	public ServletRegistrationBean<LTIServlet> ltiServlet() {
		return new ServletRegistrationBean<>(new LTIServlet(), "/lti/*");
	}

	// FIXME: Need to update dependency, basiclti-util is at last updated to 23.5 but basiclti 23.5 is missing
	//@Bean
	//public ServletRegistrationBean<LTIv2Servlet> ltiv2Servlet() {
	//	return new ServletRegistrationBean<>(new LTIv2Servlet(), "/lti2/*");
	//}

	@Bean
	public ServletRegistrationBean<MailService> mailServlet() {
		return new ServletRegistrationBean<>(new MailService(), "/mail");
	}

	@Bean
	public ServletRegistrationBean<MessageService> messageServlet() {
		return new ServletRegistrationBean<>(new MessageService(), "/message");
	}

	@Bean
	public ServletRegistrationBean<OAuth2> oauth2Servlet() {
		return new ServletRegistrationBean<>(new OAuth2(), "/oauth2");
	}

	@Bean
	public ServletRegistrationBean<PingService> pingServlet() {
		return new ServletRegistrationBean<>(new PingService(), "/ping");
	}

	@Bean
	public ServletRegistrationBean<RegisterService> registerServlet() {
		return new ServletRegistrationBean<>(new RegisterService(), "/register");
	}

	@Bean
	public ServletRegistrationBean<ReportHelper> reportHelperServlet() {
		return new ServletRegistrationBean<>(new ReportHelper(), "/vector/*");
	}

	@Bean
	public ServletRegistrationBean<ReportService> reportServlet() {
		return new ServletRegistrationBean<>(new ReportService(), "/report/*");
	}

	@Bean
	public ServletRegistrationBean<ShibeServlet> shibeServlet() {
		return new ServletRegistrationBean<>(new ShibeServlet(), "/shibe");
	}

	@Bean
	public ServletRegistrationBean<XSLService> xslServlet() {
		return new ServletRegistrationBean<>(new XSLService(), "/xsl");
	}

}
