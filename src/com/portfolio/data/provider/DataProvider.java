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
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import javax.activation.MimeType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.portfolio.security.NodeRight;


/**
 * @author vassoill
 * Le dataProvider est la classe d'accÔøΩs aux donnÔøΩes.
 * Voici l'interface qui dÔøΩfinit toutes les mÔøΩthodes possibles d'un dataProvider.
 * Ces mÔøΩthodes sont directement inspirÔøΩes des appels REST existants
 *  */
public interface DataProvider {


	/**
	 * Constructeur
	 */
	public void dataProvider();

	/**
	 * @param porfolioId Id du portfolio
	 * @param semtag Filtre sur le tag semantique
	 * @param parentUuid Filtre sur l'Uuid parent
	 * @param filterId Id du filtre personnalisÔøΩ
	 * @param filterParameters Si filtre personnalisÔøΩ, paramÔøΩtres du filtre personnalisÔøΩ
	 * @param sortId Id du tri personnalisÔøΩ
	 * @return une chaine XML
	 */
	public void writeLog(Connection c, String url, String method, String headers, String inBody, String outBody, int code);

	/// Relatif à l'authentification
	public String[] postCredentialFromXml(Connection c, Integer userId, String username, String password, String substitute) throws ServletException, IOException;
	public String getMysqlUserUid (Connection c, String login) throws Exception;
	@Deprecated
	public String getUserUidByTokenAndLogin(Connection c, String login, String token) throws Exception;
	public int deleteCredential(Connection c, int userId);
	public boolean isAdmin( Connection c, String uid );

	/// Relatif aux portfolios
	public Object getPortfolio(Connection c, MimeType outMimeType, String portfolioUuid, int userId, int groupId, String label, String resource, String files, int substid, Integer cutoff) throws Exception;
	public Object getPortfolios(Connection c, MimeType outMimeType,int userId,int groupId,Boolean portfolioActive, int substid, Boolean portfolioProject, String projectId, Boolean countOnly, String search) throws Exception;
	public Object getPortfolioByCode(Connection c, MimeType mimeType, String portfolioCode, int userId, int groupId, String resources, int substid) throws Exception;
	public String getPortfolioUuidByNodeUuid(Connection c, String nodeUuid) throws Exception ;
	public Object getPortfolioZip(MimeType mimeType, String portfolioUuid, int userId, int groupId, String label, Boolean resource, Boolean files) throws Exception;
	public String getPortfolioShared(Connection c, int user, int userId) throws SQLException;

	public Object putPortfolio(Connection c, MimeType inMimeType,MimeType outMimeType,String in, String portfolioUuid, int userId, Boolean portfolioActive, int groupId, String modelId) throws Exception;
	public Object putPortfolioConfiguration(Connection c, String portfolioUuid,Boolean portfolioActive, Integer userId);

	public Object postPortfolio(Connection c, MimeType inMimeType,MimeType outMimeType,String in,  int userId, int groupId, String modelId, int substid, boolean parseRights, String projectName) throws Exception;
	public Object postPortfolioZip(Connection c, MimeType mimeType, MimeType mimeType2,
			HttpServletRequest httpServletRequest, InputStream inputStream, int userId, int groupId, String modelId, int substid, boolean parseRights, String projectName) throws Exception;
	public Object postInstanciatePortfolio(Connection c, MimeType inMimeType, String portfolioUuid, String srcCode, String newCode, int userId, int groupId, boolean copyshared, String portfGroupName, boolean setOwner ) throws Exception;
	public Object postCopyPortfolio(Connection c, MimeType inMimeType, String portfolioUuid, String srcCode, String newCode, int userId, boolean setOwner ) throws Exception;

	public Object deletePortfolio(Connection c, String portfolioUuid, int userId, int groupId) throws Exception;

	public Object postPortfolioParserights(Connection c, String portfolioUuid, int userId);

	// Same code allowed with nodes in different portfolio, and not root node
	public boolean isCodeExist( Connection c, String code, String nodeuuid );

	/// Relatif aux modèles
	// Deprecated I think
	@Deprecated
	public Object getModels(Connection c, MimeType mimeType, int userId)throws Exception;
	@Deprecated
	public Object getModel(Connection c, MimeType mimeType, Integer modelId, int userId) throws Exception;

	@Deprecated
	public Object postModels(Connection c, MimeType mimeType, String xmlModel, int userId)throws Exception;

	/// Relatif aux noeuds
	public Object getNode(Connection c, MimeType outMimeType,String nodeUuid,boolean withChildren, int userId, int groupId, String label, Integer cutoff) throws Exception;
	public String getNodePortfolioId(Connection c, String nodeUuid ) throws Exception;
	public Object getNodes(Connection c, MimeType outMimeType, String portfolioUuid,int userId, int groupId, String semtag,String parentUuid, String filterId,String filterParameters,String sortId, Integer cutoff) throws Exception;
	public Object getNodes(Connection c, MimeType mimeType, String portfoliocode, String semtag, int userId, int groupId, String semtag_parent, String code_parent, Integer cutoff) throws SQLException;
	public Object getNodeBySemanticTag(Connection c, MimeType mimeType, String portfolioUuid, String semantictag, int userId, int groupId) throws Exception;
	Object getNodesBySemanticTag(Connection c, MimeType outMimeType, int userId, int groupId, String portfolioUuid, String semanticTag) throws SQLException;
	public Object getNodeWithXSL(Connection c, MimeType mimeType, String nodeUuid, String xslFile, String parameters, int userId, int groupId);
	public Object getNodesParent(Connection c, MimeType mimeType, String portfoliocode, String semtag, int userId, int groupId, String semtag_parent, String code_parent) throws Exception;
	public Object getNodeMetadataWad(Connection c, MimeType mimeType, String nodeUuid, boolean b, int userId, int groupId, String label) throws SQLException;
	public String getResNode(Connection c, String contextUuid, int userId, int groupId) throws Exception;
	public String getNodeRights(Connection c, String nodeUuid, int userId, int groupId) throws Exception;

	public Object putNode(Connection c, MimeType inMimeType,String nodeUuid,String in, int userId, int groupId) throws Exception;
	public Object putNodeMetadata(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception ;
	public Object putNodeMetadataWad(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception;
	public Object putNodeMetadataEpm(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId) throws Exception;
	public Object putNodeNodeContext(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId)throws Exception;
	public Object putNodeNodeResource(Connection c, MimeType mimeType, String nodeUuid, String xmlNode, int userId, int groupId)throws Exception;

	public Object postNode(Connection c, MimeType inMimeType,String parentNodeUuid,String in, int userId, int groupId, boolean forcedUuid) throws Exception;
	public Object postNodeFromModelBySemanticTag(Connection c, MimeType mimeType, String nodeUuid, String semantictag, int userId, int groupId) throws Exception;
	public Object postImportNode(Connection c, MimeType inMimeType,String destUuid,String tag, String code, String srcuuid, int userId, int groupId) throws Exception;
	public Object postCopyNode(Connection c, MimeType inMimeType,String destUuid,String tag, String code, String srcuuid, int userId, int groupId) throws Exception;

	/**
	 * @return:
	 * 	 0: OK
	 * 	-1: Invalid uuid
	 * 	-2: First node, can't move
	 */
	public int postMoveNodeUp( Connection c, int userid, String uuid );
	public boolean postChangeNodeParent( Connection c, int userid, String uuid, String uuidParent);

	public Object deleteNode(Connection c, String nodeUuid, int userId, int groupId) throws Exception;

	/// Relatif aux ressources
	public Object getResource(Connection c, MimeType outMimeType,String nodeParentUuid, int userId, int groupId) throws Exception;
	public Object getResources(Connection c, MimeType outMimeType,String portfolioUuid, int userId, int groupId) throws Exception;

	public Object putResource(Connection c, MimeType inMimeType,String nodeParentUuid,String in, int userId, int groupId) throws Exception;

	public Object postResource(Connection c, MimeType inMimeType,String nodeParentUuid,String in,int userId, int groupId) throws Exception;

	public Object deleteResource(Connection c, String resourceUuid, int userId, int groupId) throws Exception;

	/// Relatif aux utilisateurs
	public String getListUsers(Connection c, int userId, String username, String firstname, String lastname, String email);
	public Object getUser(Connection c, int userId) throws Exception;
	public String getUserID(Connection c, int currentUser, String username);
	public String getInfUser(Connection c, int userId, int userid2);
	public Object getUserGroups(Connection c, int userId) throws Exception;
	public String getUserGroupByPortfolio(Connection c, String portfolioUuid, int userId);
	// Apparently unused, keep getListUsers
	public Object getUsers(Connection c, int userId, String username, String firstname, String lastname, String email) throws Exception;
	public String getUsersByRole(Connection c, int userId, String portfolioUuid, String role) throws SQLException;

	public Object putUser(Connection c, int userId,String oAuthToken,String oAuthSecret) throws Exception;
	public String putInfUser(Connection c, int userId, int userid2, String xmlPortfolio) throws SQLException;
	public String putInfUserInternal(Connection c, int userId, int userid2, String fname, String lname , String email, String affiliation ) throws SQLException;
	public String UserChangeInfo(Connection c, int userId, int userid2, String in) throws SQLException;

	public Object postUser(Connection c, String xmluser, int userId) throws SQLException, Exception;
	public String postUsers(Connection c, String xmlUsers, int userId)throws Exception;
	public String postUsersGroups(int userId);

	public Object deleteUser(int userid, int userId1);
	public int deleteUsers(Connection c, Integer userId, Integer userid2);

	/// Relatif aux ressources
	public String getResourceNodeUuidByParentNodeUuid(Connection c, String nodeParentUuid);
	public String getRessource(Connection c, String nodeUuid,int userId,int groupId, String type) throws SQLException;

	/// Relatif aux rôles
	public int postCreateRole(Connection c, String portfolioUuid, String role, int userId);
	public String deletePersonRole(Connection c, String portfolioUuid, String role, int userId, int uid);

	/// Relatif aux groupe d'utilisateurs
	public boolean isUserMemberOfGroup(Connection c, int userId, int groupId);
	public String getRoleUser(Connection c, int userId, int userid2);
	public String getUserGroupList(Connection c, int userId);
	public int getGroupByGroupLabel(Connection c, String groupLabel, int userId);
	public String getGroupByUser(Connection c, int user, int userId);
	public String getUsersByUserGroup(Connection c, int userGroupId, int userId);
	public String getGroupsByRole(Connection c, int userId, String portfolioUuid, String role);
	public String getGroupsPortfolio(Connection c, String portfolioUuid, int userId);
	public Integer getRoleByNode( Connection c, int userId, String nodeUuid, String role );

	public Boolean putUserGroupLabel(Connection c, Integer user, int siteGroupId, String label);
	public Integer putUserGroup(Connection c, String siteGroupId, String userId);
	public Boolean putUserInUserGroup(Connection c, int user, int siteGroupId, int currentUid);

	public Object postGroup(Connection c, String xmlgroup, int userId) throws Exception ;
	public boolean postGroupsUsers(Connection c, int user, int userId, int groupId);
	public int postUserGroup(Connection c, String label, int userid);

	public Boolean deleteUsersGroups(Connection c, int usersgroup, int currentUid);
	public Boolean deleteUsersFromUserGroups(Connection c, int userId, int usersgroup, int currentUid);

	/// Related to portfolio groups
	public int postPortfolioGroup( Connection c, String groupname, String type, Integer parent, int userId );
	public int getPortfolioGroupIdFromLabel( Connection c, String groupLabel, int userId );
	public String getPortfolioGroupListFromPortfolio(Connection c, String portfolioid,  int userId );
	public String getPortfolioGroupList( Connection c, int userId );
	public String getPortfolioByPortfolioGroup( Connection c, Integer portfolioGroupId, int userId );
	public String deletePortfolioGroups( Connection c, int portfolioGroupId, int userId );
	public int putPortfolioInGroup( Connection c, String uuid, Integer portfolioGroupId, String label, int userId );
	public String deletePortfolioFromPortfolioGroups( Connection c, String uuid, int portfolioGroupId, int userId );

	/// Relatif aux groupe de droits
	public Object getGroupRights(Connection c, int userId, int groupId) throws Exception;
	public String getGroupRightsInfos(Connection c, int userId, String portfolioId) throws SQLException;
	public String getRolePortfolio(Connection c, MimeType mimeType, String role, String portfolioId, int userId)throws SQLException;
	String getRole(Connection c, MimeType mimeType, int grid, int userId) throws SQLException;

	public Object putRole(Connection c, String xmlRole, int userId, int roleId)throws Exception;

	public String postRoleUser(Connection c, int userId, int grid, Integer userid2)throws SQLException;
	boolean postNodeRight(int userId, String nodeUuid) throws Exception;
	public boolean postRightGroup(Connection c, int groupRightId, int groupId, Integer userId);
	public boolean postNotifyRoles(Connection c, int userId, String portfolio, String uuid, String notify);
	public boolean setPublicState(Connection c, int userId, String portfolio, boolean isPublic);
	@Deprecated
	public int postShareGroup(Connection c, String portfolio, int user, Integer userId, String write);

	public int deleteShareGroup(Connection c, String portfolio, Integer userId);
	public int deleteSharePerson(Connection c,String portfolio, int user, Integer userId);
	public Object deleteGroupRights(Connection c, Integer groupId, Integer groupRightId, Integer userId);

	/// √Ä propos des macro-commandes pour la modification des droits
	/// e.g.: submit, show/hide; ainsi que la partie gestion de ces commandes
	public String postMacroOnNode(Connection c, int userId, String nodeUuid, String macroName);

	/// √Ä propos de la gestion des groupes de droits
	public String getRRGList(Connection c, int userId, String portfolio, Integer user, String role);
	public String getRRGInfo(Connection c, int userId, Integer rrgid);
	public String getPortfolioInfo(Connection c, int userId, String portId);
	public String[] getPorfolioGroup( int userId, String groupName );

	public String putRRGUpdate(Connection c, int userId, Integer rrgId, String data);

	public String postRRGCreate(Connection c, int userId, String portfolio, String data);
	public String postRRGUsers( Connection c, int userId, Integer rrgid, String data );
	public String postRRGUser(Connection c, int userId, Integer rrgid, Integer user);
	public String postRights(Connection c, int userId, String uuid, String role, NodeRight rights);

	public String deleteRRG(Connection c, int userId, Integer rrgId);
	public String deleteRRGUser(Connection c, int userId, Integer rrgId, Integer user);
	public String deletePortfolioUser( Connection c, int userId, String portId );

	//// LTI related
	public String getUserId(Connection c, String username, String email) throws Exception;
	public String createUser(Connection c, String username, String email) throws Exception;
	public String getGroupByName( Connection c, String name );
	public String createGroup( Connection c, String name );
	public boolean isUserInGroup( Connection c, String uid, String gid );

	//// Notification related
	public Set<String[]> getNotificationUserList( Connection c, int userId, int groupId, String uuid );
	public boolean touchPortfolio( Connection c, String fromNodeuuid, String fromPortuuid );

	//// Other queries
	// Check if email exist in system
	public String[] logViaEmail( Connection c, String email );
	public String emailFromLogin( Connection c, String username );
	public boolean changePassword( Connection c, String username, String password );
	public boolean registerUser( Connection c, String username, String password );
}
