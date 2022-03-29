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

import java.util.Properties;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletConfig;

import org.slf4j.Logger;

public class MailUtils {
    //===============================================================
    private static class SMTPAuthenticator extends javax.mail.Authenticator {
        //===============================================================
        private final javax.mail.PasswordAuthentication authentication;

        public SMTPAuthenticator(String login, String password) {
            authentication = new javax.mail.PasswordAuthentication(login, password);
        }

        @Override
        protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
            return authentication;
        }
    }

    //===============================================================
    public static int postMail(ServletConfig config, String recipients_to, String recipients_cc, String subject, String content, Logger logger) throws Exception {
        //===============================================================
        if (recipients_to == null || subject == null || content == null)
            return -1;

        boolean debug = false;
        logger.debug("<br>postMail --in");

        String[] recip = recipients_to.split(",");
        String[] recip_cc = null;
        if (recipients_cc != null && !"".equals(recipients_cc))
            recip_cc = recipients_cc.split(",");

        try {
            final String mail_login = ConfigUtils.getInstance().getRequiredProperty("mail_login");
            String mail_sender = ConfigUtils.getInstance().getProperty("mail_sender");
            final String mail_password = ConfigUtils.getInstance().getProperty("mail_password");

            if (mail_sender == null)
                mail_sender = mail_login;

            //Set the host smtp address
            Properties props = new Properties();

            final String useAuth = ConfigUtils.getInstance().getRequiredProperty("smtp.useauth");

            props.put("mail.smtp.host", ConfigUtils.getInstance().getRequiredProperty("smtp.server"));
            props.put("mail.smtp.auth", useAuth);
            props.put("mail.smtp.port", ConfigUtils.getInstance().getRequiredProperty("smtp.port"));
            props.put("mail.smtp.starttls.enable", ConfigUtils.getInstance().getRequiredProperty("smtp.starttls"));
            props.put("mail.smtp.ssl.protocols", ConfigUtils.getInstance().getRequiredProperty("smtp.tlsver"));

            logger.debug("SMTP properties used: {}", props);

            Session session;
            if ("true".equals(useAuth)) {
                javax.mail.Authenticator auth = new SMTPAuthenticator(mail_login, mail_password);
                session = Session.getInstance(props, auth);
            } else {
                session = Session.getInstance(props);
            }

            session.setDebug(debug);

            // create a message
            Message msg = new MimeMessage(session);

            // set the from and to address
            InternetAddress addressFrom = new InternetAddress(mail_sender);
            msg.setFrom(addressFrom);
            logger.debug("<br>addressFrom: {}", addressFrom);

            InternetAddress[] addressTo = new InternetAddress[recip.length];
            for (int i = 0; i < recip.length; i++) {
                addressTo[i] = new InternetAddress(recip[i]);
                logger.debug("<br>addressTo: {}", addressTo[i]);
            }
            if (recip_cc != null) {
                InternetAddress[] addressCC = new InternetAddress[recip_cc.length];
                for (int i = 0; i < recip_cc.length; i++) {
                    addressCC[i] = new InternetAddress(recip_cc[i]);
                    logger.debug("<br>addressTo: {}", addressCC[i]);
                }
                msg.setRecipients(Message.RecipientType.CC, addressCC);
            }
            msg.setRecipients(Message.RecipientType.TO, addressTo);
            MimeMultipart multipart = new MimeMultipart("related");

            // first part  (the html)
            MimeBodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setHeader("Content-Type", "text/html; charset=\"utf-8\"");
            messageBodyPart.setContent(content, "text/html; charset=utf-8");

            // add it
            multipart.addBodyPart(messageBodyPart);

            // put everything together
            msg.setContent(multipart);

            // Setting the Subject and Content Type
            msg.setSubject(subject);
            Transport.send(msg);
            logger.debug("<br>postMail --out");

            return 0;
        } catch (IllegalStateException e) {
            logger.error("Required properties aren't set to send email", e);
            return -1;
        }
    }

	/*
	private static HashMap<String, String> loadConfig( String filename ) throws IOException
	{
		HashMap<String, String> config = new HashMap<String, String>();
		java.io.FileInputStream fichierSrce =  new java.io.FileInputStream(filename);
		java.io.BufferedReader readerSrce = new java.io.BufferedReader(new java.io.InputStreamReader(fichierSrce, StandardCharsets.UTF_8));
		String line = null;
		String variable = null;
		String value = null;
		while ((line = readerSrce.readLine())!=null){
			if (!line.startsWith("#") && line.length()>2) { // ce n'est pas un commentaire et longueur>=3 ex: x=b est le minumum
				String[] tok = line.split("=");
				variable = tok[0];
				value = tok[1];
				config.put(variable, value);
			}
		}
		fichierSrce.close();

		return config;
	}
	//*/
}