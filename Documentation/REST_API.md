Base REST address are defined from: **{server}/rest/api**
- [Login/Logout](#login)
- [User info](#user)
- [Portfolio](#portfolios)
- [Groups](#groups)
- [Nodes](#nodes)
- [RRG](#rrg)
- [Resource](#resource)
- [Admin](#admin)
- [Social](#social)

## <a name="login"></a>Login
Concern current user information logged in the system
[User.md](./User.md)

## <a name="user"></a>User info
Concern user information in the system
[UsersInfo.md](./UsersInfo.md)

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

[Portfolio.md](./Portfolio.md)

## <a name="nodes"></a>NODES

[Nodes.md](./Nodes.md)

## <a name="rrg"></a>RRG

[RRG.md](./RRG.md)

## <a name="resource"></a>Resource

[Resource.md](./Resource.md)

## <a name="admin"></a>Admin
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

