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

package com.eportfolium.karuta.data.attachment;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class PingService extends HttpServlet {
	/**
	 *
	 */
	private static final long serialVersionUID = 4969750715693599979L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) {
		final HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute("uid") == null) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		response.setStatus(HttpServletResponse.SC_OK);
	}
}