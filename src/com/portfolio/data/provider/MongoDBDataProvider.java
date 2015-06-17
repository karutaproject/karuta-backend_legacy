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

	@Override
	public void setConnection( Connection c )
	{

	}


	/*
	@Override
	public void setDataSource( DataSource source )
	{
		// TODO Auto-generated method stub
	}
	//*/

	@Override
	public Object deleteNode(String nodeUuid, int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object deletePortfolio(String portfolioUuid, int userId, int groupId)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object deleteResource(String resourceUuid, int userId, int groupId)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getNode(MimeType outMimeType, String nodeUuid,
			boolean withChildren, int userId, int groupId, String label) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getNodes(MimeType outMimeType, String portfolioUuid,
			int userId,int groupId,
			String semtag, String parentUuid, String filterId,
			String filterParameters, String sortId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getPortfolio(MimeType outMimeType, String portfolioUuid,
			int userId, int groupId, String label, String resource, String files, int substid) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getPortfolios(MimeType outMimeType, int userId,int groupId, Boolean portfolioActive, int substid)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getResource(MimeType outMimeType, String nodeParentUuid,
			int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int postCreateRole(String portfolioUuid, String role, int userId)
	{
		return 0;
	}

	@Override
	public String deletePersonRole(String portfolioUuid, String role, int userId, int uid)
	{
		return null;
	}

	@Override
	public Object getResources(MimeType outMimeType, String portfolioUuid,
			int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getUser(int userId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postInstanciatePortfolio( MimeType inMimeType, String portfolioUuid, String srcCode, String newCode, int userId, int groupId, boolean copyshared, String portfGroupName, boolean setOwner ) throws Exception
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postCopyPortfolio(MimeType inMimeType, String portfolioUuid, String srcCode, String newCode, int userId, boolean setOwner ) throws Exception
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postImportNode( MimeType inMimeType, String destUuid, String tag,
			String code, int userId, int groupId ) throws Exception
			{
		// TODO Auto-generated method stub
		return null;
			}

	@Override
	public Object postCopyNode( MimeType inMimeType, String destUuid, String tag,
			String code, int userId, int groupId ) throws Exception
			{
		// TODO Auto-generated method stub
		return null;
			}

	@Override
	public int postMoveNodeUp( int userid, String uuid )
	{
		return -1;
	}

	@Override
	public boolean postChangeNodeParent( int userid, String uuid, String uuidParent)
	{
		return false;
	}

	@Override
	public Object postNode(MimeType inMimeType, String parentNodeUuid,
			String in, int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postPortfolio(MimeType inMimeType, MimeType outMimeType,
			String in, int userId, int groupId, String modelId, int substid, boolean parseRights ) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postResource(MimeType inMimeType, String nodeParentUuid,String in, int userId, int groupId)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putNode(MimeType inMimeType, String nodeUuid,
			String in, int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isCodeExist( String code )
	{
		return false;
	}

	@Override
	public Object putPortfolio(MimeType inMimeType, MimeType outMimeType,
			String in, String portfolioUuid, int userId, Boolean portfolioActive, int groupId, String modelId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putResource(MimeType inMimeType, String nodeParentUuid,
			String in, int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putUser(int userId, String oAuthToken, String oAuthSecret)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void writeLog(String url, String method, String headers, String inBody, String outBody, int code)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void disconnect(){


	}

	@Override
	public String getResourceNodeUuidByParentNodeUuid(String nodeParentUuid)
	{
		return null;
	}

	@Override
	public Object putPortfolioConfiguration(String portfolioUuid,
			Boolean portfolioActive, Integer userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getPortfolioByCode(MimeType mimeType, String portfolioCode,
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
	public String getMysqlUserUid(String login) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}



	@Override
	public String getUserUidByTokenAndLogin(String login, String token)
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
	public Object getUserGroups(int userId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isUserMemberOfGroup(int userId, int groupId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object postGroup(String xmlgroup, int userId) throws Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String[] getUsers(int userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGroupRights(int userId, int groupId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean postGroupsUsers(int user, int userId, int groupName) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean postRightGroup(int groupRightId, int groupId, Integer userId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean postNotifyRoles(int userId, String portfolio, String uuid, String notify) {
		return false;
	}

	@Override
	public boolean setPublicState(int userId, String portfolio, boolean isPublic)
	{
		return false;
	}

	@Override
	public int postShareGroup(String portfolio, int user, Integer userId, String write)
	{
		return 0;
	}

	@Override
	public int deleteShareGroup(String portfolio, Integer userId)
	{
		return 0;
	}

	@Override
	public int deleteSharePerson(String portfolio, int user, Integer userId)
	{
		return 0;
	}

	@Override
	public Object deleteUsers(Integer userId, Integer userId2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object deleteGroupRights(Integer groupId, Integer groupRightId, Integer userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postPortfolioZip(MimeType mimeType, MimeType mimeType2,
			HttpServletRequest httpServletRequest, int userId, int groupId, String modelId, int substid, boolean parseRights) throws FileNotFoundException, IOException
			{
		// TODO Auto-generated method stub
		return null;
			}

	@Override
	public String getPortfolioUuidByNodeUuid(String nodeUuid) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postUser(String xmluser, int user) throws SQLException, Exception {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Object getNodeBySemanticTag(MimeType mimeType, String portfolioUuid,
			String semantictag, int userId, int groupId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGroupRightsInfos(int userId, String portfolioId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getNodesBySemanticTag(MimeType outMimeType, int userId,
			int groupId, String portfolioUuid, String semanticTag)
					throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String getListUsers(int userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] postCredentialFromXml(Integer userId, String username, String password, String substitute) throws ServletException,
	IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getUserID( int currentUser, String username )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getInfUser(int userId, int userid) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int deleteCredential(int userId) {
		// TODO Auto-generated method stub
		return 0;
	}


	@Override
	public String getRoleUser(int userId, int userid2) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String putInfUser(int userId, int userid2, String xmlPortfolio) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String postUsers(String xmlUsers, int userId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object deleteUser(int userid, int userId1) {
		// TODO Auto-generated method stub
		return null;
	}





	@Override
	public Object getNodeWithXSL(MimeType mimeType, String nodeUuid,
			String xslFile, String parameters, int userId, int groupId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postNodeFromModelBySemanticTag(MimeType mimeType,
			String nodeUuid, String semantictag, int userId, int groupId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGroupsPortfolio(String portfolioUuid, int userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer getRoleByNode( int userId, String nodeUuid, String role )
	{
		return null;
	}


	@Override
	public String postRoleUser(int userId, int grid, Integer userid2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getRolePortfolio(MimeType mimeType, String role,
			String portfolioId, int userId) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getUsersByRole(int userId, String portfolioUuid, String role)
			throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getGroupsByRole(int userId, String portfolioUuid, String role) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String postMacroOnNode( int userId, String nodeUuid, String macroName )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getRole(MimeType mimeType, int grid, int userId)
			throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getUserGroupByPortfolio(String portfolioUuid, int userId) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String postUsersGroups(int userId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer putUserGroup(String usergroup, String userPut) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer putUserInUserGroup(int user, int siteGroupId, int currentUid)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getUserGroupList(int userId)
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getUsersByUserGroup(int userId, int groupId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deleteUsersGroups(int userId, int usersgroup) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deleteUsersFromUserGroups(int userId, int usersgroup, int userid2) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String postUserGroup(String label, int userid) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getNodeMetadataWad(MimeType mimeType, String nodeUuid,
			boolean b, int userId, int groupId, String label) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getResNode(String contextUuid, int userId, int groupId) throws Exception
	{
	// TODO Auto-generated method stub
	return null;
	}

	@Override
	public String getNodeRights(String nodeUuid, int userId, int groupId) throws Exception
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putNodeMetadata(MimeType mimeType, String nodeUuid,
			String xmlNode, int userId, int groupId) throws Exception  {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putNodeMetadataWad(MimeType mimeType, String nodeUuid,
			String xmlNode, int userId, int groupId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putNodeMetadataEpm(MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception
	{
		return null;
	}

	@Override
	public Object putNodeNodeContext(MimeType mimeType, String nodeUuid,
			String xmlNode, int userId, int groupId) throws Exception{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putNodeNodeResource(MimeType mimeType, String nodeUuid,
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
	public String getRRGList( int userId, String portfolio, Integer user, String role )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getRRGInfo( int userId, Integer rrgid )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getPortfolioInfo( int userId, String portId )
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
	public String putRRGUpdate( int userId, Integer rrgId, String data )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String postRRGCreate( int userId, String portfolio, String data )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String postRRGUsers( int userId, Integer rrgid, String data )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String postRights(int userId, String uuid, String role, NodeRight rights)
	{
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public String postRRGUser( int userId, Integer rrgid, Integer user )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deleteRRG( int userId, Integer rrgId )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deleteRRGUser( int userId, Integer rrgId, Integer user )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String deletePortfolioUser( int userId, String portId )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object putRole(String xmlRole, int userId, int roleId) throws Exception{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getModels(MimeType mimeType, int userId) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getModel(MimeType mimeType, Integer modelId, int userId)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object postModels(MimeType mimeType, String xmlModel, int userId)
			throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object getNodes(MimeType mimeType, String portfoliocode, String semtag,
			int userId, int groupId, String semtag_parent, String code_parent) throws SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getRessource(String nodeUuid, int userId, int groupId, String type) throws SQLException{
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
	public Object getNodesParent(MimeType mimeType, String portfoliocode,
			String semtag, int userId, int groupId, String semtag_parent,
			String code_parent) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isAdmin( String uid )
	{
		return false;
	}

	@Override
	public String getUserId(String username, String email) throws Exception
	{
		return null;
	}

	@Override
	public String createUser(String username) throws Exception
	{
		return null;
	}

	@Override
	public String getGroupByName( String name )
	{
		return null;
	}

	@Override
	public String createGroup( String name )
	{
		return null;
	}

	@Override
	public boolean isUserInGroup( String uid, String gid )
	{
		return false;
	};

	@Override
	public Set<String[]> getNotificationUserList( int userId, int groupId, String uuid )
	{
		return null;
	}

	@Override
	public boolean touchPortfolio( String fromNodeuuid, String fromPortuuid  )
	{
		return false;
	}

	@Override
	public String[] logViaEmail( String email )
	{
		return null;
	}

	@Override
	public String emailFromLogin( String username )
	{
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean changePassword( String username, String password )
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean registerUser( String username, String password )
	{
		// TODO Auto-generated method stub
		return false;
	}

}
