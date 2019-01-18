Karuta as service provider (SP)
=====

- Install Shibboleth
- Generate certificate with the shibboleth keygen.sh

File editing
-
Some informations are provided by the institution tech service, kindly ask them about it.
- /etc/shibboleth/shibboleth2.xml

>\<ApplicationDefaults entityID="Karuta"  
>sessionHook="https://{MY SERVER}/karuta-backend/shibe"  
>REMOTE_USER="{USER ATTRIBUTE}"  
>attributePrefix="AJP_">

entityID= Application name  
sessionHook = Interface for shibboleth communication  
REMOTE_USER= variable that will be considered as username
>\<SSO entityID="https://{REMOTE SERVER}/idp/shibboleth">  
>SAML2 SAML1  
>\</SSO>

SSO entityID  Identity Provider server

>\<MetadataProvider type="XML"  
>uri="http://{REMOTE SERVER}/idp/shibboleth"  
>backingFilePath="/var/shibboleth/idp-{REMOTE}-metadata.xml"  
>reloadInterval="7200"></MetadataProvider>

uri: Identity Provider server address  
backingFilePath: Local xml file describing the remote identity provider, usually located at "http://{REMOTE SERVER}/idp/shibboleth"
 
- /etc/shibboleth/attribute-map.xml

List of attributes needed sent by the identity provider server.  
They are put inside the /rest/api/credential information, need matching attribute name inside `shib_attrib` from `configKaruta.properties`  
Need at least the username

>\<Attribute name="urn:mace:dir:attribute-def:{USERNAME}" id="{USER ATTRIBUTE}" />  
>\<Attribute name="urn:oid:{OID}" id="{USER ATTRIBUTE}" />  
>\<Attribute name="urn:mace:dir:attribute-def:dob" id="dob" />  
>\<Attribute name="urn:oid:{OID}" id="dob"/>

Service testing
-
Start the shibboleth server and check if it's active:
>https://{MY SERVER}/Shibboleth.sso/Metadata

Connection redirection
-
Edit apache for redirection
>\<Location />  >AuthType shibboleth  >ShibRequestSetting requireSession true  >require valid-user  >\</Location>

Other
-
If needed, add Institution specific webpages
in /etc/shibboleth/ (e.g.: local-logout.html, accessError.html, sessionError.html, metadataError.html)