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

import java.net.URLEncoder;

import org.json.JSONException;
import org.json.JSONObject;

import com.portfolio.data.utils.FileUtils;


public class Elgg {

	private static String DEFAULT_API_URL;

	private static String DEFAULT_SITE_URL;

	private static String ELGG_API_AUTH_KEY;





	private final String username;
    private final String token;

	public Elgg(String elggDefaultApiUrl,String elggDefaultSiteUrl,String elggApiKey,String username,String elggDefaultUserPassword) throws Exception
	{
		this.username = username;
		DEFAULT_API_URL = elggDefaultApiUrl;
		DEFAULT_SITE_URL = elggDefaultSiteUrl;
		ELGG_API_AUTH_KEY = elggApiKey;
		token = postAuthGetToken(username,elggDefaultUserPassword);
		if(token==null)
		{
			throw new Exception("Unable to auth. user "+username+" to Elgg");
		}

	}

	public String postAuthGetToken(String username,String password)
	{
		String status = null;
		String result = "";
		try {
		JSONObject obj = new JSONObject(FileUtils.postHTTPQuery(DEFAULT_API_URL,"method=auth.gettoken&username="+username+"&password="+password));

			status = obj.getString("status");
			result = obj.getString("result");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		return result;
	}

	public String postWire(String message)
	{

		String status = null;
		String result = "";
		try {
		JSONObject obj = new JSONObject(FileUtils.postHTTPQuery(DEFAULT_API_URL,"method=wire.save_post&api_key="+ELGG_API_AUTH_KEY+"&username="+username+"&auth_token="+token+"&text="+URLEncoder.encode(message)));

		result = "";
		//	status = obj.getString("status");
		//	result = obj.getString("result");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		return result;
	}

	public String getSiteRiverFeed(int limit)
	{
		String result = "";

		try {
			JSONObject obj = new JSONObject(unEscapeResult(FileUtils.getHTTPQuery(DEFAULT_API_URL+"?method=site.river_feed&auth_token="+token+"&limit="+limit)));


			return obj.toString();

			/*
			JSONArray arr = obj.getJSONArray("result");
			for (int i = 0; i < arr.length(); i++)
			{
			    String string = arr.getJSONObject(i).getString("string");
			    String type = arr.getJSONObject(i).getString("type");
			    String subtype = arr.getJSONObject(i).getString("subtype");

			    JSONObject subjectMetadata = arr.getJSONObject(i).getJSONObject("subject_metadata");
		    	String subjectMetadataName = subjectMetadata.getString("name");
		    	String subjectMetadataUserName = subjectMetadata.getString("username");
		    	String subjectMetadataAvatarUrl = subjectMetadata.getString("avatar_url");


			    JSONObject objectMetadata = arr.getJSONObject(i).getJSONObject("object_metadata");
			    	String objectMetadataName = objectMetadata.getString("name");
			    	String objectMetadataUserName = "";
			    	try
			    	{
			    		objectMetadataUserName = objectMetadata.getString("username");
			    	} catch(Exception ex) {};
			    	String objectMetadataDescription = objectMetadata.getString("description");



			    result += getObjectLink(type,subtype,"<img src=\""+subjectMetadataAvatarUrl+"\">",subjectMetadataUserName) + getObjectLink(type,subtype,subjectMetadataName,subjectMetadataUserName) +string +" "+ getObjectLink(type,subtype,objectMetadataName,objectMetadataUserName);
			    if(type.equals("object") && subtype.equals("blog"))  result += "<br/><i>"+objectMetadataDescription+"</i>";
			    result += "<br/>";
			}
			*/

		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}

	public String unEscapeResult(String s)
	{
		s = s.replace("\\/","/");
		return s;
	}

	public String getObjectLink(String type, String subtype, String name, String username)
	{
		String url = "";
		if(username.startsWith("group:"))
		{
			username = username.replace("group:","");
			url = DEFAULT_SITE_URL+"groups/profile/"+username;
		}
		else url = DEFAULT_SITE_URL+"profile/"+username;

		return "<a href=\""+url+"\">"+name+"</a> ";
	}



}
