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

package com.portfolio.rest;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;


public class RestWebApplicationException extends WebApplicationException
{
	Status stat;
	String msg;
	public RestWebApplicationException(Status status, String message)
	{
		super(Response.status(status).entity(message).type(MediaType.TEXT_PLAIN).build());
		msg = message;
		stat = status;
	}
	public String getCustomMessage(){ return msg; }
	public Status getStatus(){ return stat; }
}
