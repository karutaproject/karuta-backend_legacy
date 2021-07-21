CAS
===
### configKaruta.properties
	# ==== CAS ====
	#casCreateAccount=y
	#casUrlValidation=https://{CAS_SERVER}/serviceValidate
* If you need accounts created automatically, uncomment **casCreateAccount=y**

### application/js/_init.js
	var cas_url = "";
URL to the CAS login page

LDAP
===
If CAS is setup to create accounts, LDAP can be used to fill the needed info

	# ==== LDAP ======
	#ldap.provider.url=ldap://xxx.yyy:389
	#ldap.provider.useSSL=Y
	#ldap.context.name=username
	#ldap.context.credential=MotDePasse
	#ldap.baseDn=ou=people,dc=u-cergy,dc=fr
	# Can also be (&(supannEtablissement=xxxx)(uid=%u))
	#ldap.parameter=(uid=%u)
	#ldap.user.firstname=sn
	#ldap.user.lastname=givenName
	#ldap.user.mail=mail
* *ldap.context.name*: account name used to access LDAP
* *ldap.context.credential*: associated password
* *ldap.parameter*: query used to find user info, where %u is the user id provided by CAS
