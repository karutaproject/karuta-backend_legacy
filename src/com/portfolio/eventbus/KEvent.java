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

package com.portfolio.eventbus;

import java.util.HashMap;

import javax.ws.rs.core.MediaType;

import org.w3c.dom.Document;

public class KEvent
{
	public static enum EventType {LOGIN, LOGOUT, PORTFOLIO, NODE, FILE};
	public static enum RequestType {GET, POST, PUT, DELETE};
	public static enum InputType {BINARY, TEXT, PARAMETER};
	public static enum OutputType {BINARY, XML, JSON};
	public static enum DataType {COMMENT}

	public EventType eventType;
	public RequestType requestType;
	public InputType inputType;
	public OutputType outputType;
	public String mediaType = MediaType.APPLICATION_XML;
	public DataType dataType;

	public String uuid = null;   // uuid being edited (portfolio/node/context)
	public String message = null;
	public String inputData = null;  // Raw input data
	public HashMap<String, String> inputParameter = null;    // Parameters given
	public Integer nodeOwner = null;
	public int status = 500;

	public Document doc = null;  // Exchange format, current edition state
}
