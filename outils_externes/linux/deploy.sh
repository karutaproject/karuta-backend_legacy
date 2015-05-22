#!/bin/sh

/usr/bin/scp $HOME/Documents/IUT2-BACKEND.war root@tanami.upmf-grenoble.fr:/tmp
/usr/bin/ssh root@tanami.upmf-grenoble.fr chmod 777 /tmp/IUT2-BACKEND.war
/usr/bin/ssh root@tanami.upmf-grenoble.fr cp -pf /tmp/IUT2-BACKEND.war /var/lib/tomcat6/webapps/
