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

package com.eportfolium.karuta.socialnetwork;

import java.util.List;

import com.ning.api.client.NingClient;
import com.ning.api.client.access.Activities;
import com.ning.api.client.access.NingConnection;
import com.ning.api.client.action.PagedList;
import com.ning.api.client.auth.ConsumerKey;
import com.ning.api.client.item.Activity;
import com.ning.api.client.item.ActivityField;
import com.ning.api.client.item.Author;
import com.ning.api.client.item.Image;
import com.ning.api.client.item.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ning {

    public final static Logger logger = LoggerFactory.getLogger(Ning.class);

    public final static String DEFAULT_XAPI_HOST = "external.ningapis.com";

    // 'www' is used for bootstrapping (listing Networks that user owns)
    public final static String DEFAULT_NETWORK = "iut2grenoble";

    public final static int DEFAULT_HTTP_PORT = 80;
    public final static int DEFAULT_HTTPS_PORT = 443;

    // bogus ones: need to externalize

    public final static String CONSUMER_KEY = "4ee3a012-7dfb-4551-b2fb-aaed61e49f41";
    public final static String CONSUMER_SECRET = "ec8f1382-6beb-44b0-9905-ac8f8c549d2c";

    private Token token;
    private NingConnection ningConnection = null;

    public Ning() {

        ConsumerKey consumerAuth = new ConsumerKey(CONSUMER_KEY, CONSUMER_SECRET);
        NingClient ningClient = new NingClient(DEFAULT_NETWORK, consumerAuth, DEFAULT_XAPI_HOST, DEFAULT_HTTP_PORT, DEFAULT_HTTPS_PORT);
        try {

            token = ningClient.createToken("marc.vassoille@iut2.upmf-grenoble.fr", "marguerite38");
            ningConnection = ningClient.connect(token);
        } catch (Exception ex) {
            logger.error("Managed error", ex);
        }

    }

    public PagedList<Activity> getActivites() {
        Activities a = ningConnection.activities();

        Activities.Lister lister = a.listerForRecent(ActivityField.title, ActivityField.type,
                ActivityField.author,
                ActivityField.attachedTo,
                ActivityField.attachedToAuthor,
                ActivityField.attachedToAuthor_fullName,
                ActivityField.image_url,
                ActivityField.description

        );

        return lister.list();
    }

    public String getXhtmlActivites() {
        StringBuilder xHtml = new StringBuilder();
        int entry = 0;

        List<Activity> acts = null;
        PagedList<Activity> list = getActivites();

        do {
            acts = list.next(3);
            for (Activity act : acts) {
                ++entry;

                xHtml.append(" activity #").append(entry).append(" -> ").append(toString(act)).append("<br>");

            }
        } while (!acts.isEmpty());


        return xHtml.toString();
    }


    static String toString(Activity act) {
        String base = "activity of type " + act.getType() + ", title '" + act.getTitle() + "', author: " + act.getAuthor();
        Author auth = act.getAttachedToAuthorResource();
        if (auth == null) {
            base += ", author info UNKNOWN";
        } else {
            base += ", author name: " + auth.getFullName();
        }
        Image image = act.getImageResource();
        if (image == null) {
            base += ", NO image";
        } else {
            base += ", image url: " + image.getUrl();
        }
        return base;
    }


}