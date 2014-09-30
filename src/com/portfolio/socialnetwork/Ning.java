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

package com.portfolio.socialnetwork;

import java.util.ArrayList;
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

public class Ning {

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

	public Ning()
	{

		ConsumerKey consumerAuth = new ConsumerKey(CONSUMER_KEY, CONSUMER_SECRET);
		NingClient ningClient = new NingClient(DEFAULT_NETWORK, consumerAuth,DEFAULT_XAPI_HOST,DEFAULT_HTTP_PORT,DEFAULT_HTTPS_PORT);
		try {

			token = ningClient.createToken("marc.vassoille@iut2.upmf-grenoble.fr", "marguerite38");
			ningConnection =  ningClient.connect(token);
		}
		catch(Exception ex)
		{
			System.out.println(ex.getMessage());
		}

	}

	public PagedList<Activity> getActivites()
	{
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

	public String getXhtmlActivites()
	{
		String xHtml = "";
		int entry = 0;

		 	List<Activity> acts = null;
	        PagedList<Activity> list = getActivites();
	        String s = "---";
	       // System.out.println("First, iterate over list in chunks of 3");
	        int i = 0;
	        ArrayList ningActivities = new ArrayList();
	        do {
	           // System.out.println("Request #"+(entry/3)+" (anchor="+list.position()+"):");
	            s += "Request #"+(entry/3)+" (anchor="+list.position()+"):<br/>";
	            acts = list.next(3);
	            i++;

	            for (Activity act : acts) {
	                ++entry;

	                //System.out.println(" activity #"+entry+" -> "+toString(act));
	                xHtml += " activity #"+entry+" -> "+toString(act)+"<br>";


	               /* Author auth = act.getAttachedToAuthorResource();
	                if(act.getAuthor()!=null && auth!=null) ningActivity.setAuthor(auth.getFullName());
	                if(act.getCreatedDate()!=null) ningActivity.setCreatedDate(act.getCreatedDate().toString());
	                ningActivity.setDescription(act.getDescription());
	                if(act.getContentId()!=null) ningActivity.setId(act.getContentId().toString());
	                if(act.getTitle()!=null) ningActivity.setTitle(act.toString());

	                ningActivities.add(ningActivity);*/
	            }
	        } while (!acts.isEmpty());
	       // } while (i<2);


	        return xHtml;
		}




	static String toString(Activity act)
	{
		    String base = "activity of type "+act.getType()+", title '"+act.getTitle()+"', author: "+act.getAuthor();
		    Author auth = act.getAttachedToAuthorResource();
		    if (auth == null) {
		        base += ", author info UNKNOWN";
		    } else {
		        base += ", author name: "+auth.getFullName();
		    }
		    Image image = act.getImageResource();
		    if (image == null) {
		        base += ", NO image";
		    } else {
		        base += ", image url: "+image.getUrl();
		    }
		    return base;
	}




}
