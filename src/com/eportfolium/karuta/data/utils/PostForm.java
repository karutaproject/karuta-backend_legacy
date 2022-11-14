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

package com.eportfolium.karuta.data.utils;

import java.io.File;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class PostForm {
    public static boolean sendFile(String sessionid, String backend, String user, String uuid, String lang, File file) throws Exception {

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            // Server + "/resources/resource/file/" + uuid +"?lang="+ lang
            // "http://"+backend+"/user/"+user+"/file/"+uuid+"/"+lang+"ptype/fs";
            String url = backend + "/resources/resource/file/" + uuid + "?lang=" + lang;
            HttpPost post = new HttpPost(url);
            post.setHeader("Cookie", "JSESSIONID=" + sessionid);    // So that the receiving servlet allow us

            /// Remove import language tag
            String filename = file.getName();    /// NOTE: Since it's used with zip import, specific code.
            int langindex = filename.lastIndexOf("_");
            filename = filename.substring(0, langindex) + filename.substring(langindex + 3);

            FileBody bin = new FileBody(file, ContentType.DEFAULT_BINARY, filename);    // File from import

            /// Form info
            HttpEntity reqEntity = MultipartEntityBuilder.create()
                    .addPart("uploadfile", bin)
                    .build();
            post.setEntity(reqEntity);

            CloseableHttpResponse response = httpclient.execute(post);

			/*
			try
			{
				HttpEntity resEntity = response.getEntity();	/// Will be JSON
				if( resEntity != null )
				{
					BufferedReader reader = new BufferedReader(new InputStreamReader(resEntity.getContent(), StandardCharsets.UTF_8));
					StringBuilder builder = new StringBuilder();
					for( String line = null; (line = reader.readLine()) != null; )
						builder.append(line).append("\n");

					updateResource( sessionid, backend, uuid, lang, builder.toString() );
				}
				EntityUtils.consume(resEntity);
			}
			finally
			{
				response.close();
			}
			//*/
        }

        return true;
    }

    public static boolean rewriteFile(String sessionid, String backend, String user, String uuid, String lang, File file) throws Exception {

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            // Server + "/resources/resource/file/" + uuid +"?lang="+ lang
            // "http://"+backend+"/user/"+user+"/file/"+uuid+"/"+lang+"ptype/fs";
            String url = backend + "/resources/resource/file/" + uuid + "?lang=" + lang;
            HttpPut put = new HttpPut(url);
            put.setHeader("Cookie", "JSESSIONID=" + sessionid);    // So that the receiving servlet allow us

            /// Remove import language tag
            String filename = file.getName();    /// NOTE: Since it's used with zip import, specific code.
            int langindex = filename.lastIndexOf("_");
            filename = filename.substring(0, langindex) + filename.substring(langindex + 3);

            FileBody bin = new FileBody(file, ContentType.DEFAULT_BINARY, filename);    // File from import

            /// Form info
            HttpEntity reqEntity = MultipartEntityBuilder.create()
                    .addPart("uploadfile", bin)
                    .build();
            put.setEntity(reqEntity);

            CloseableHttpResponse response = httpclient.execute(put);

			/*
			try
			{
				HttpEntity resEntity = response.getEntity();	/// Will be JSON
				if( resEntity != null )
				{
					BufferedReader reader = new BufferedReader(new InputStreamReader(resEntity.getContent(), StandardCharsets.UTF_8));
					StringBuilder builder = new StringBuilder();
					for( String line = null; (line = reader.readLine()) != null; )
						builder.append(line).append("\n");

					updateResource( sessionid, backend, uuid, lang, builder.toString() );
				}
				EntityUtils.consume(resEntity);
			}
			finally
			{
				response.close();
			}
			//*/
        }

        return true;
    }

    public static boolean updateResource(String sessionid, String backend, String uuid, String lang, String json) throws Exception {
        /// Parse and create xml from JSON
        JSONObject files = (JSONObject) JSONValue.parse(json);
        JSONArray array = (JSONArray) files.get("files");

        if ("".equals(lang) || lang == null)
            lang = "fr";

        JSONObject obj = (JSONObject) array.get(0);
        String ressource = "";
        String attLang = " lang=\"" + lang + "\"";
        ressource += "<asmResource>" +
                "<filename" + attLang + ">" + obj.get("name") + "</filename>" + // filename
                "<size" + attLang + ">" + obj.get("size") + "</size>" +
                "<type" + attLang + ">" + obj.get("type") + "</type>" +
//		obj.get("url");	// Backend source, when there is multiple backend
                "<fileid" + attLang + ">" + obj.get("fileid") + "</fileid>" +
                "</asmResource>";


        /// Send data to resource
        /// Server + "/resources/resource/file/" + uuid +"?lang="+ lang
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPut put = new HttpPut("http://" + backend + "/rest/api/resources/resource/" + uuid);
            put.setHeader("Cookie", "JSESSIONID=" + sessionid);    // So that the receiving servlet allow us

            StringEntity se = new StringEntity(ressource);
            se.setContentEncoding("application/xml");
            put.setEntity(se);

            CloseableHttpResponse response = httpclient.execute(put);

            try {
                HttpEntity resEntity = response.getEntity();
            } finally {
                response.close();
            }
        }

        return false;
    }
}