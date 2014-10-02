Base REST address are defined from: **/rest/api**
- [Login/Logout](#login)
- [User info](#user)
- [Portfolio](#portfolios)
- [Groups](#groups)
- [Nodes](#nodes)
- [RRG](#rrg)
- [Ressource](#ressource)
- [Admin](#admin)
- [Social](#social)

## <a name="login"></a>Login

/credential:
GET: Fetch current user information, if not logged, return 401
> Parameters:
> None
> 
> Return:
> 
	<user id="{userid}">
		<username>{username}</username>
		<firstname>{First name}</firstname>
		<lastname>{Last name}</lastname>
		<email>{email}</email>
		<admin>{is admin? 0|1}</admin>
		<designer>{is designer? 0|1}</designer>
		<active>{is active? 0|1}</active>
	</user>

/credential/login
PUT, POST: XML, login
> Data sent: 
> 
	<credential>
		<login>username</login>
		<password>password</password>
	</credential>
> Return:
> 
	<credential>
		<useridentifier>{username}</useridentifier>
		<token>{some_number}</token>
		<firstname>{First name}</firstname>
		<lastname>{Last name}</lastname>
		<admin>{is admin? 0|1}</admin>
		<designer>{is designer? 0|1}</designer>
		<email>{email}</email>
	</credential>

/credential/logout
POST: Logout
> Data sent:
> None
> 
> Return:
>
 	logout

/credential/group/{group-id}:
POST: Force role selection, when a person has multiple role in a portfolio
> Data sent:
> None
> 
> Return: None

/credential/group/{portfolio-id}:
GET: Les rôles d'un utilisateurs dans un portfolio (auto select si il y en a un)
> Parameters:
> None
> 
> Return:
>
	<groups>
		<group id={group id} templateId={role id}>
			<label>{group label}</label>
			<role>{role label}<role>
			<groupid>{group id}</groupid>
		</group>
		...
	</groups>

## <a name="user"></a>User info

/users:
GET user list
> Parameters:
> None
> 
> Return:
>
	<users>
		<user id="{user id}">
			<username>{username}</username>
			<firstname>{first name}</firstname>
			<lastname>{last name}</lastname>
			<admin>{is admin? 0|1}</admin>
			<designer>{is designer? 0|1}</designer>
			<email>{email}</email>
			<active>{is active? 0|1}</active>
		</user>
		...
	</users>

POST: create user
> Data sent:
> 
	<users>
		<user>
			<username>{username}</username>
			<password>{password}</password>
			<firstname>{first name}</firstname>
			<lastname>{last name}</lastname>
			<email>{email}</email>
			<active>{0|1}</active>
			<designer>{0|1}</designer>
		</user>
		...
	</users>
> 
> Return:
>
	<users>
		<user id="{user id}">
			<username>{username}</username>
			<password>{password}</password>
			<firstname>{first name}</firstname>
			<lastname>{last name}</lastname>
			<email>{email}</email>
			<active>{is active? 0|1}</active>
			<designer>{is designer? 0|1}</designer>
		</user>
		...
	</users>

DELETE: delete user
> Parameters:
>
	userId={userid}
> Return:
> 
	supprimé
	Nothing

/users/user/{user-id}
GET: info on user
> Parameters:
> None
> 
> Return:
> 
	<user id="{user id}">
		<username>{username}</username>
		<password>{password}</password>	
		<firstname>{first name}</firstname>
		<lastname>{last name}</lastname>
		<email>{email}</email>
		<active>{is active? 0|1}</active>
		<designer>{is designer? 0|1}</designer>
	</user>


PUT: update info	CODE FORMAT
> Data sent:
> 
	<user id="{user id}">
		<username>{username}</username>
		<password>{password}</password>	
		<firstname>{first name}</firstname>
		<lastname>{last name}</lastname>
		<email>{email}</email>
		<admin>{0|1}</admin>
		<active>{0|1}</active>
		<designer>{0|1}</designer>
	</user>
> Return:
> 
	{user id}

DELETE: delete user
> Parameters:
>
	userId={userid}
> Return:
> 
	user {user id} deleted

/users/user/username/{username}
GET: fetch user id from username
> Parameters:
> None
>
> Return:
> 
	{user id}

/users/user/{user-id}/groups
GET: groups from a user
> Parameters:
> None
>
> Return:
> 
	<profiles id="{user id}">
		<profile>
			<username>{username}</username>
			<password>{password}</password>
			<firstname>{first name}</firstname>
			<lastname>{last name}</lastname>
			<email>{email}</email>
			<admin>{0|1}</admin>
			<active>{0|1}</active>
			<designer>{0|1}</designer>
		<profile>
	</profiles>

## <a name="groups"></a>Groups

/groupRights:
GET: fetch uuid and rights from roles

/groups:
GET: Groups current user is part of

/groupRightsInfos:
GET: fetch rôles from portfolios

/group
POST: Création d'un groupe d'utilisateurs

/groups/{portfolio-id}
GET récupère les groupes d'un portfolio

/groupUsers
POST: Ajout de personnes dans un group d'utilisateurs

/RightGroup (?)
POST: Modifie le rôle attaché à un groupe de personnes

## <a name="portfolios"></a>PORTFOLIOS

/portfolios/portfolio/{portfolio-id}
GET: Fetch porfolio via ID
PUT: Rewrite portfolio	SEND XML
DELETE: Efface un portfolio et groupe associé

/portfolios/portfolio/code/{code}
GET: Fetch portfolio via code

/portfolios:
GET: Portfolio list accessible for this user
PUT: Change portfolio state FORMAT?
POST: XML Avec xml, importe

/portfolios/zip:
POST: zip, importe avec fichiers

/portfolios/instanciate/{portfolio-id}
POST: Instancie un portfolio et évalue les droits spécifié

/portfolios/copy/{portfolio-id}
POST: Copie brut d'un portfolio sans évaluation de droits


## <a name="nodes"></a>NODES
/nodes/node/{node-id}
GET get info
PUT: Replace node

/nodes/node/{node-id}/children
GET: node info and children

/nodes/node/{node-id}/metadatawad
GET: node metadata
PUT: replace metadata-wad

/nodes/firstbysemantictag/{portfolio-id}/{semantictag}
GET: Query, fetch node via semantic tag in portfolio

/nodes/bysemantictag/{portfolio-id}/{semantictag}
GET: Query, fetch node via semantic tag in portfolio

/nodes/node/{nodeid}/nodecontext
PUT: Replace nodecontext

/nodes/node/{nodeid}/noderesource
PUT: Replace resource

/nodes/node/import/{dest-id}?srcetag={tag}&srcecode={code}
POST: Instancie un noeud et évalue les droits des attributs

/nodes/node/copy/{dest-id}?srcetag={tag}&srcecode={code}
POST: Copie brute d'un noeud

/nodes?portfoliocode={code}&semtag={tag}&semtag_parent={parent-tag}&code_parent={parent-code}
GET: récupère des noeuds selon code et tag

/nodes/node/{parent-id} ??
PSOT: Ajoute un noeud
DELETE: Eface le noeud et enfants

/nodes/node/{node-id}/moveup
POST: Monte le noeud d'une place

/nodes/node/{node-id}/parentof/{parent-id}
POST: Bouge le noeud sous un autre parent

/nodes/node/{node-id}/action/{action-name}
POST: Execute l'action au noeud

/nodes/{node-id}/xsl/{xsl-filename}
GET: fetch processed node

/nodes/{node-id}/frommodelbysemantictag/{semantic-tag}
POST

## <a name="rrg"></a>RRG
/rolerightsgroups
GET: récupère info de portfolio 

/rolerightsgorups/{portfolio-id}
POST crée un rôle

/rolerightsgroups/all/users
GET récupère les droits selon
DELETE: Retire l'utilisateur d'un rôle

/rolerightsgroups/rolerightsgroup/{rrg-id}
GET Infos d'un rôle
PUT: Modifie info d'un rôle
DELETE: Efface rrd et utilisateurs associés

## <a name="ressource"></a>Ressource

/resources/resource/{parent-id}
GET: Ressource d'un noeud
PUT: Replace ressource d'un noeud

## <a name="admin"></a>ADMIN
/users/Portfolio/{portfolio-id}/Role/{role}/users
GET: récupère les rôle dans un portfolio

/users/Portfolio/{portfolio-id}/Role/{role}/groups
GET: récupère l'info de groupe à partir du rôle (?)

/usersgroups
GET get groups groups

## <a name="social"></a>Social
/ning/activities
GET: ning stuff

/elgg/site/river_feed
GET: elgg stuff

