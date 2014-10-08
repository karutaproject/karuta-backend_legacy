### /rolerightsgroups?portfolio={portfolio-id}
GET: récupère info de portfolio 
> Parameters:
> None
>
> Return:
>
	<rolerightsgroups>
		<rolerightsgroup id="...">
			<label>all</label>
			<portfolio id="..."></portfolio>
		</rolerightsgroup>
		<rolerightsgroup id="...">
			<label>...</label>
			<portfolio id="..."></portfolio>
		</rolerightsgroup>
	</rolerightsgroups>

### /rolerightsgorups/{portfolio-id}
POST crée un rôle
> Data sent:
>
	<rolerightsgroups>
		<rolerightsgroup>
			<label>...</label>
			<portfolio id='...'/>
			<role>...</role>
			<active>...</active>
			<users>
				<user id='...'/>
				...
			</users> 
		</rolerightsgroup>
		...
	</rolerightsgroups>
> Return:
>
	<rolerightsgroups>
		<rolerightsgroup id='...'>
			<label>...</label>
			<portfolio id='...'/>
			<role>...</role>
			<active>...</active>
			<users>
				<user id='...'/>
				...
			</users>
		</rolerightsgroup>
		...
	</rolerightsgroups> 

### /rolerightsgroups/all/users?portfolio={portfolioid}
GET récupère les droits selon
> Parameters:
> None
>
> Return:
>
	<portfolio id="...">
		<rrg id="...">
			<label>...</label>
			<users>...</users>
		</rrg>
		<rrg id="...">
			<label>...</label>
			<users>...</users>
		</rrg>
	</portfolio>

### /rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users/user/{user-id}
DELETE: Retire l'utilisateur d'un rôle
> Parameters:
> None
>
> Return:
> None

### /rolerightsgroups/rolerightsgroup/{rolerightsgroup-id}/users
POST:
> Parameters:
> None
>
> Return:
>
	<users>
		<user id='...'/>
		<user id='...'/>
		...
	</users>

DELETE: Efface les utilisateurs
> Parameters:
> None
>
> Return:
> None

### /rolerightsgroups/rolerightsgroup/{rrg-id}
GET Infos d'un rôle
> Parameters:
> None
>
> Return:
>
	<rolerightsgroup id='...'>
		<label>...</label>
		<portfolio id='...'/>
		<role>...</role>
		<active>...</active>
		<users>
			<user id='...'>
				<username>...</username>
				<firstname>...</firstname>
				<lastname>...</lastname>
				<email>...</email>
				<active>...</active>
			</user>
			<user id='...'>
				<username>...</username>
				<firstname>...</firstname>
				<lastname>...</lastname>
				<email>...</email>
				<active>...</active>
			</user>
			...
		</users>
	</rolerightsgroup> 

PUT: Modifie info d'un rôle
> Parameters:
> None
>
> Return:
>
	<rolerightsgroup id='...'>
		<label>...</label>
		<role>...</role>
	</rolerightsgroup>

DELETE: Efface rrd et utilisateurs associés
> Parameters:
> None
>
> Return:
> None
