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
