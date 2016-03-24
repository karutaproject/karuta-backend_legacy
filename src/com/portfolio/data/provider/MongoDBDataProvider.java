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

package com.portfolio.data.provider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import javax.activation.MimeType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.portfolio.security.Credential;
import com.portfolio.security.NodeRight;

public class MongoDBDataProvider implements DataProvider {

	/*
	@Override
	public void connect(Properties connectionProperties) throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public Credential getCredential()
	{
		return null;
	}
	//*/

	@Override
	public void dataProvider() {
		// TODO Auto-generated method stub
	}

	/*
	@Override
	public void setDataSource( DataSource source )
	{
		// TODO Auto-generated method stub
	}
	//*/

	@Override
	public Object deleteNode(Connection c, String nodeUuid, int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object deletePortfolio(Connection c, String portfolioUuid, int userId, int groupId)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object deleteResource(Connection c, String resourceUuid, int userId, int groupId)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getNode(Connection c, MimeType outMimeType, String nodeUuid,
			boolean withChildren, int userId, int groupId, String label) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getNodes(Connection c, MimeType outMimeType, String portfolioUuid,
			int userId,int groupId,
			String semtag, String parentUuid, String filterId,
			String filterParameters, String sortId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getPortfolio(Connection c, MimeType outMimeType, String portfolioUuid,
			int userId, int groupId, String label, String resource, String files, int substid, String cutoff) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getPortfolios(Connection c, MimeType outMimeType, int userId,int groupId, Boolean portfolioActive, int substid)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getResource(Connection c, MimeType outMimeType, String nodeParentUuid,
			int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int postCreateRole(Connection c, String portfolioUuid, String role, int userId)
	{
		return 0;
	}

	@Override
	public String deletePersonRole(Connection c, String portfolioUuid, String role, int userId, int uid)
	{
		return null;
	}

	@Override
	public Object getResources(Connection c, MimeType outMimeType, String portfolioUuid,
			int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getUser(Connection c, int userId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postInstanciatePortfolio( Connection c, MimeType inMimeType, String portfolioUuid, String srcCode, String newCode, int userId, int groupId, boolean copyshared, String portfGroupName, boolean setOwner ) throws Exception
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postCopyPortfolio(Connection c, MimeType inMimeType, String portfolioUuid, String srcCode, String newCode, int userId, boolean setOwner ) throws Exception
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postImportNode( Connection c, MimeType inMimeType, String destUuid, String tag,
			String code, String srcuuid, int userId, int groupId ) throws Exception
			{
		// TODO Auto-generated method stub
		return null;
			}

	@Override
	public Object postCopyNode( Connection c, MimeType inMimeType, String destUuid, String tag,
			String code, String srcuuid, int userId, int groupId ) throws Exception
			{
		// TODO Auto-generated method stub
		return null;
			}

	@Override
	public int postMoveNodeUp( Connection c, int userid, String uuid )
	{
		return -1;
	}

	@Override
	public boolean postChangeNodeParent( Connection c, int userid, String uuid, String uuidParent)
	{
		return false;
	}

	@Override
	public Object postNode(Connection c, MimeType inMimeType, String parentNodeUuid,
			String in, int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postPortfolio(Connection c, MimeType inMimeType, MimeType outMimeType,
			String in, int userId, int groupId, String modelId, int substid, boolean parseRights ) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postResource(Connection c, MimeType inMimeType, String nodeParentUuid,String in, int userId, int groupId)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putNode(Connection c, MimeType inMimeType, String nodeUuid,
			String in, int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isCodeExist( Connection c, String code )
	{
		return false;
	}

	@Override
	public Object putPortfolio(Connection c, MimeType inMimeType, MimeType outMimeType,
			String in, String portfolioUuid, int userId, Boolean portfolioActive, int groupId, String modelId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putResource(Connection c, MimeType inMimeType, String nodeParentUuid,
			String in, int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putUser(Connection c, int userId, String oAuthToken, String oAuthSecret)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void writeLog(Connection c, String url, String method, String headers, String inBody, String outBody, int code)
	{
		// TODO Auto-generated method stub

	}

	/*
	@Override
	public void disconnect(){


	}
	//*/

	@Override
	public String getResourceNodeUuidByParentNodeUuid(Connection c, String nodeParentUuid)
	{
		return null;
	}

	@Override
	public Object putPortfolioConfiguration(Connection c, String portfolioUuid,
			Boolean portfolioActive, Integer userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getPortfolioByCode(Connection c, MimeType mimeType, String portfolioCode,
			int userId, int groupId,String resources, int substid) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] postCredential(String login, String password, Integer UserId) throws ServletException, IOException {
		return null;
		// TODO Auto-generated method stub

	}

	@Override
	public void getCredential(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getMysqlUserUid(Connection c, String login) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}



	@Override
	public String getUserUidByTokenAndLogin(Connection c, String login, String token)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean postNodeRight(int userId, String nodeUuid) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object getUserGroups(Connection c, int userId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isUserMemberOfGroup(Connection c, int userId, int groupId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object postGroup(Connection c, String xmlgroup, int userId) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String[] getUsers(Connection c, int userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGroupRights(Connection c, int userId, int groupId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean postGroupsUsers(Connection c, int user, int userId, int groupName) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean postRightGroup(Connection c, int groupRightId, int groupId, Integer userId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean postNotifyRoles(Connection c, int userId, String portfolio, String uuid, String notify) {
		return false;
	}

	@Override
	public boolean setPublicState(Connection c, int userId, String portfolio, boolean isPublic)
	{
		return false;
	}

	@Override
	public int postShareGroup(Connection c, String portfolio, int user, Integer userId, String write)
	{
		return 0;
	}

	@Override
	public int deleteShareGroup(Connection c, String portfolio, Integer userId)
	{
		return 0;
	}

	@Override
	public int deleteSharePerson(Connection c, String portfolio, int user, Integer userId)
	{
		return 0;
	}

	@Override
	public Object deleteUsers(Connection c, Integer userId, Integer userId2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object deleteGroupRights(Connection c, Integer groupId, Integer groupRightId, Integer userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postPortfolioZip(Connection c, MimeType mimeType, MimeType mimeType2,
			HttpServletRequest httpServletRequest, int userId, int groupId, String modelId, int substid, boolean parseRights) throws FileNotFoundException, IOException
			{
		// TODO Auto-generated method stub
		return null;
			}

	@Override
	public String getPortfolioUuidByNodeUuid(Connection c, String nodeUuid) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postUser(Connection c, String xmluser, int user) throws SQLException, Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object getNodeBySemanticTag(Connection c, MimeType mimeType, String portfolioUuid,
			String semantictag, int userId, int groupId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGroupRightsInfos(Connection c, int userId, String portfolioId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getNodesBySemanticTag(Connection c, MimeType outMimeType, int userId,
			int groupId, String portfolioUuid, String semanticTag)
					throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String getListUsers(Connection c, int userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] postCredentialFromXml(Connection c, Integer userId, String username, String password, String substitute) throws ServletException,
	IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getUserID( Connection c, int currentUser, String username )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getInfUser(Connection c, int userId, int userid) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int deleteCredential(Connection c, int userId) {
		// TODO Auto-generated method stub
		return 0;
	}


	@Override
	public String getRoleUser(Connection c, int userId, int userid2) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String putInfUser(Connection c, int userId, int userid2, String xmlPortfolio) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String postUsers(Connection c, String xmlUsers, int userId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object deleteUser(int userid, int userId1) {
		// TODO Auto-generated method stub
		return null;
	}





	@Override
	public Object getNodeWithXSL(Connection c, MimeType mimeType, String nodeUuid,
			String xslFile, String parameters, int userId, int groupId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postNodeFromModelBySemanticTag(Connection c, MimeType mimeType,
			String nodeUuid, String semantictag, int userId, int groupId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGroupsPortfolio(Connection c, String portfolioUuid, int userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer getRoleByNode( Connection c, int userId, String nodeUuid, String role )
	{
		return null;
	}


	@Override
	public String postRoleUser(Connection c, int userId, int grid, Integer userid2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getRolePortfolio(Connection c, MimeType mimeType, String role,
			String portfolioId, int userId) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getUsersByRole(Connection c, int userId, String portfolioUuid, String role)
			throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGroupsByRole(Connection c, int userId, String portfolioUuid, String role) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String postMacroOnNode( Connection c, int userId, String nodeUuid, String macroName )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getRole(Connection c, MimeType mimeType, int grid, int userId)
			throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getUserGroupByPortfolio(Connection c, String portfolioUuid, int userId) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String postUsersGroups(int userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer putUserGroup(Connection c, String usergroup, String userPut) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Boolean putUserGroupLabel(Connection c, Integer user, int siteGroupId, String label)
	{
		return false;
	}

	@Override
	public Boolean putUserInUserGroup(Connection c, int user, int siteGroupId, int currentUid)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getUserGroupList(Connection c, int userId)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGroupByUser(Connection c, int user, int userId)
	{
		return null;
	}

	@Override
	public String getUsersByUserGroup(Connection c, int userId, int groupId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Boolean deleteUsersGroups(Connection c, int usersgroup, int currentUid) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Boolean deleteUsersFromUserGroups(Connection c, int userId, int usersgroup, int currentUid) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int postUserGroup(Connection c, String label, int userid) {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public int postPortfolioGroup( Connection c, String groupname, String type, Integer parent, int userId )
	{
		return 0;
	}
	
	@Override
	public String getPortfolioGroupListFromPortfolio(Connection c, String portfolioid,  int userId )
	{
		return null;
	}
	
	@Override
	public String getPortfolioGroupList( Connection c, int userId )
	{
		return null;
	}

	@Override
	public String getPortfolioByPortfolioGroup( Connection c, Integer portfolioGroupId, int userId )
	{
		return null;
	}

	@Override
	public String deletePortfolioGroups( Connection c, int portfolioGroupId, int userId )
	{
		return null;
	}

	@Override
	public int putPortfolioInGroup( Connection c, String uuid, Integer portfolioGroupId, String label, int userId )
	{
		return 0;
	}

	@Override
	public String deletePortfolioFromPortfolioGroups( Connection c, String uuid, int portfolioGroupId, int userId )
	{
		return null;
	}

	@Override
	public Object getNodeMetadataWad(Connection c, MimeType mimeType, String nodeUuid,
			boolean b, int userId, int groupId, String label) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getResNode(Connection c, String contextUuid, int userId, int groupId) throws Exception
	{
	// TODO Auto-generated method stub
	return null;
	}

	@Override
	public String getNodeRights(Connection c, String nodeUuid, int userId, int groupId) throws Exception
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putNodeMetadata(Connection c, MimeType mimeType, String nodeUuid,
			String xmlNode, int userId, int groupId) throws Exception  {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putNodeMetadataWad(Connection c, MimeType mimeType, String nodeUuid,
			String xmlNode, int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putNodeMetadataEpm(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception
	{
		return null;
	}

	@Override
	public Object putNodeNodeContext(Connection c, MimeType mimeType, String nodeUuid,
			String xmlNode, int userId, int groupId) throws Exception{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putNodeNodeResource(Connection c, MimeType mimeType, String nodeUuid,
			String xmlNode, int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	@Override
	public Connection getConnection() {
		// TODO Auto-generated method stub
		return null;
	}
	//*/

	@Override
	public String getRRGList( Connection c, int userId, String portfolio, Integer user, String role )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getRRGInfo( Connection c, int userId, Integer rrgid )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getPortfolioInfo( Connection c, int userId, String portId )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] getPorfolioGroup( int userId, String groupName )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String putRRGUpdate( Connection c, int userId, Integer rrgId, String data )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String postRRGCreate( Connection c, int userId, String portfolio, String data )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String postRRGUsers( Connection c, int userId, Integer rrgid, String data )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String postRights(Connection c, int userId, String uuid, String role, NodeRight rights)
	{
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String postRRGUser( Connection c, int userId, Integer rrgid, Integer user )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deleteRRG( Connection c, int userId, Integer rrgId )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deleteRRGUser( Connection c, int userId, Integer rrgId, Integer user )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deletePortfolioUser( Connection c, int userId, String portId )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putRole(Connection c, String xmlRole, int userId, int roleId) throws Exception{
		// TODO Auto-generated method stub
		return null;
	}

	@Deprecated
	@Override
	public Object getModels(Connection c, MimeType mimeType, int userId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Deprecated
	@Override
	public Object getModel(Connection c, MimeType mimeType, Integer modelId, int userId)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Deprecated
	@Override
	public Object postModels(Connection c, MimeType mimeType, String xmlModel, int userId)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getNodes(Connection c, MimeType mimeType, String portfoliocode, String semtag,
			int userId, int groupId, String semtag_parent, String code_parent) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getRessource(Connection c, String nodeUuid, int userId, int groupId, String type) throws SQLException{
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public Object getPortfolioZip(MimeType mimeType, String portfolioUuid,
			int userId, int groupId, String label, Boolean resource, Boolean files)
					throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getNodesParent(Connection c, MimeType mimeType, String portfoliocode,
			String semtag, int userId, int groupId, String semtag_parent,
			String code_parent) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAdmin( Connection c, String uid )
	{
		return false;
	}

	@Override
	public String getUserId(Connection c, String username, String email) throws Exception
	{
		return null;
	}

	@Override
	public String createUser(Connection c, String username) throws Exception
	{
		return null;
	}

	@Override
	public String getGroupByName( Connection c, String name )
	{
		return null;
	}

	@Override
	public String createGroup( Connection c, String name )
	{
		return null;
	}

	@Override
	public boolean isUserInGroup( Connection c, String uid, String gid )
	{
		return false;
	};

	@Override
	public Set<String[]> getNotificationUserList( Connection c, int userId, int groupId, String uuid )
	{
		return null;
	}

	@Override
	public boolean touchPortfolio( Connection c, String fromNodeuuid, String fromPortuuid  )
	{
		return false;
	}

	@Override
	public String[] logViaEmail( Connection c, String email )
	{
		return null;
	}

	@Override
	public String emailFromLogin( Connection c, String username )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean changePassword( Connection c, String username, String password )
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean registerUser( Connection c, String username, String password )
	{
		// TODO Auto-generated method stub
		return false;
	}

}
