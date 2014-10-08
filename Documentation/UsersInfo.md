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

POST: create users
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
