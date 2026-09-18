package com.eportfolium.karuta.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;

import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.eportfolium.karuta.data.utils.ConfigUtils;

public class ConnexionLdap {

	private static final Logger logger = LoggerFactory.getLogger(ConnexionLdap.class);

	private Hashtable<String, String> env;
	private ArrayList<Attribute> attr;
	private String site;

	private final String providerUrl;
	private final String principal;
	private final String credential;
	private final String contextName;
	private final String userFilter;
	private final boolean checkSSL;
	private final String attribFirstName;
	private final String attribLastName;
	private final String attribMail;
	private final String attribAffiliation;

	public ConnexionLdap() {
		providerUrl = ConfigUtils.getInstance().getRequiredProperty("ldap.provider.url");
		checkSSL = BooleanUtils.toBoolean(ConfigUtils.getInstance().getProperty("ldap.provider.useSSL"));
		principal = ConfigUtils.getInstance().getRequiredProperty("ldap.context.name");
		credential = ConfigUtils.getInstance().getRequiredProperty("ldap.context.credential");

		userFilter = ConfigUtils.getInstance().getProperty("ldap.parameter");
		contextName = ConfigUtils.getInstance().getRequiredProperty("ldap.baseDn");

		attribFirstName = ConfigUtils.getInstance().getRequiredProperty("ldap.user.firstname");
		attribLastName = ConfigUtils.getInstance().getRequiredProperty("ldap.user.lastname");
		attribMail = ConfigUtils.getInstance().getRequiredProperty("ldap.user.mail");
		attribAffiliation = ConfigUtils.getInstance().getRequiredProperty("ldap.user.affiliation");
	}

	public ArrayList<Attribute> getAttr() {
		return attr;
	}

	public Hashtable<String, String> getEnv() {
		return env;
	}

	public String[] getLdapValue(String usern) throws NamingException, IOException {

		final Attributes matchAttrs = new BasicAttributes(true);

		// recuperation des propriétés
		final var env = new Hashtable<String, Object>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, providerUrl);

		env.put(Context.SECURITY_AUTHENTICATION, "simple");
		env.put(Context.SECURITY_PRINCIPAL, principal);
		env.put(Context.SECURITY_CREDENTIALS, credential);

		if (checkSSL) {
			env.put(Context.SECURITY_PROTOCOL, "ssl");
			//		env.put("java.naming.ldap.factory.socket", "javax.net.ssl.SSLSocketFactory");
		}

		/// Limit return values
		final String[] returnAttrib = { attribFirstName, attribLastName, attribMail };

		final var controle = new SearchControls();
		controle.setReturningAttributes(returnAttrib);
		controle.setSearchScope(SearchControls.SUBTREE_SCOPE);

		final var critere = userFilter.replace("%u", usern); //filtre LDAP avec %u = userid (cas)
		//String critere = String.format("(%s=%s)", checkParam, usern);

		final DirContext ictx = new InitialDirContext(env);
		final var e = ictx.search(contextName, critere, controle);
		final String retval = null;
		String fname = null;
		String lname = null;
		String mail = null;
		String affiliation = null;
		if (e.hasMore()) {
			final var r = e.next();

			final var attribs = r.getAttributes();
			final var fobj = attribs.get(attribFirstName);
			if (fobj != null) {
				fname = fobj.get().toString();
			} else {
				fname = "";
			}

			final var lobj = attribs.get(attribLastName);
			if (lobj != null) {
				lname = lobj.get().toString();
			} else {
				lname = "";
			}

			final var mobj = attribs.get(attribMail);
			if (mobj != null) {
				mail = mobj.get().toString();
			} else {
				mail = "";
			}

			final var affiobj = attribs.get(attribAffiliation);
			if (affiobj != null) {
				affiliation = affiobj.get().toString();
			} else {
				affiliation = "";
			}
		}
		ictx.close();// fermeture de la connexion au ldap

		return new String[] { retval, fname, lname, mail, affiliation };
	}

	public String getSite() {
		return site;
	}

	public void listerAttributs(Attributes atts) throws javax.naming.NamingException {

		String displayName;
		String givenName;
		String sn;
		String mail;
		//String affiliation;

		displayName = (String) atts.get("displayName").get();
		givenName = (String) atts.get("givenName").get();
		sn = (String) atts.get("sn").get();
		mail = (String) atts.get("mail").get();

		logger.info("displayName: {}", displayName);
		logger.info("mail: {}", mail);

	}

	public void setAttr(ArrayList<Attribute> attr) {
		this.attr = attr;
	}

	public void setEnv(Hashtable<String, String> env) {
		this.env = env;
	}

	public void setSite(String site) {
		this.site = site;
	}
}