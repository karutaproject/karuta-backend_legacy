Karuta
======
##### Karuta-backend is the backend of Karuta.

Karuta is a very flexible environment to design, test and deploy simple and sophisticated learning and evaluation portfolios. Karuta exploits and enriches the hierarchical structure of web pages with a comprehensive set of specialized resources and semantic tags. The objects (trees, nodes, leafs), which are manipulated through a rich-client approach based on jQuery javascript library, are displayed with CSS from the Twitter Bootstrap library and saved with a comprehensive set of REST APIs implemented in Java and MySQL (Oracle forthcoming). With Karuta,  a designer can quickly construct very sophisticated workflows where different users (students, instructors, etc.) are assigned to a wide range of actions (reflect, upload files, comment, evaluate, grade with rubrics, etc.) without the help of a developer.  Karuta integrates the Responsive Design approach and can thus be displayed on various mobile devices.  Karuta has built-in import and export functionalities. 

-------
#### Download Karuta
To download executable files go to [ePortfolium website](https://www.eportfolium.com)

-------
#### Installing Karuta Source Code
Tomcat 9 is recommended.

MySQL 5.5 is recommended.

#### Back-end
1. Download karuta-backend zip file from karuta-backend repository
2. Unpack and import as existing project in Eclipse
3. Edit and import the sql file in the "sql" directory with the desired database name
4. Edit "WebContent/META-INF/context.xml" with the appropriate values
5. Add Project Facets : Dynamic Web Module and Java to the project
6. Run on server
7. Optional: set the jvm param `karuta.home` or the environment property `KARUTA_HOME` to customize the path of the config files. If not set it will use ${catalina.base}/${servletName}_config/
8. Optional: set the jvm param `karuta.report-folder` or the environment property `KARUTA_REPORT_FOLDER` to customize the path of the reporting log files. If not set it will go on ${catalina.base}/logs/${servletName}_logs/

#### Simple file server
1. Download karuta-fileserver zip file from karuta-fileserver repository
2. Unpack and import as existing project in Eclipse
3. Create a *RestFileServer_config* into the webapps working directory of Eclipse
4. Copy WebContent/persistence_config.properties to the *RestFileServer_config* directory
5. Run on server

#### Front-end
1. Download karuta-frontend zip file from karuta-frontend repository
2. Unpack and import as existing project in Eclipse
3. Run on server
4. Open localhost:8080/karuta in a browser
5. Connect as *root* with password *mati* to start