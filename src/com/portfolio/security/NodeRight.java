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

package com.portfolio.security;

public class NodeRight {

	public int groupId=0;
	public int rrgId=0;
	public String groupLabel="";
  public Boolean add;
	public Boolean read;
	public Boolean write;
	public Boolean submit;
	public Boolean delete;
	public Boolean lier;

	public NodeRight(Boolean add, Boolean read, Boolean write, Boolean submit, Boolean delete, Boolean lier)
	{
	  this.add = add;
		this.read = read;
		this.write = write;
		this.submit = submit;
		this.delete = delete;
		this.lier = lier;
	}

}
