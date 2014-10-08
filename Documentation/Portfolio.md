### /portfolios[/portfolio/{portfolio­id}|/code/{code}[?resources=true]]resources=true&files=true

GET: Fetch porfolio via ID
> Parameters:
> None
>
> Return:
>
	<portfolio id='...'>
		<version>...</version>
		<metadata-wad prog='main.jsp'/>
		<asmRoot id='...' resid='...'>
		<metadata  semantictag='' sharedNode='N' sharedNodeResource='N'/>
		<metadata-wad />
		<asmResource xsi_type='nodeRes'>
			<code>...</code>
			<label lang='fr'>...</label>
			<label lang='en'>...</label>
			<description lang='fr'/>
			<description lang='en'/>
		</asmResource>
		<asmStructure>
		...
		</asmStructure>
	</asmRoot>
	</portfolio> 

PUT: Rewrite portfolio	SEND XML
> Parameters:
> None
>
> Return:
>

DELETE: Efface un portfolio et groupe associé
> Parameters:
> None
>
> Return:
>

### /portfolios?active=1|0:
GET: Portfolio list accessible for this user
> Parameters:
> None
>
> Return:
>
	<portfolios>
		<portfolio id=’...’  modified=’...’>
			<asmRoot id=’...’ resid=’...’>
				<asmResource id=’...’ xsi_type='nodeRes'>
					<code></code>
					<label lang=’fr’>...</label>
					<label lang=’en’>...</label>
					<description lang=’fr’>...</description>
					<description lang=’en’>...</description>
				</asmResource>
			</asmRoot>
		</portfolio>
		...
	</portfolios>

### /portfolios?active=1|0:
PUT: Change portfolio state FORMAT?
> Parameters:
> None
>
> Return:
> None

POST: XML Avec xml, importe

### /portfolios/zip:
POST: zip, importe avec fichiers
> Data sent:
> zip, format :
>
> Return:
> Portfolio UUID

### /portfolios/copy|instantiate/{portfolidid}?targetcode={targetcode}
POST: Instancie un portfolio et évalue les droits spécifié
> Parameters:
> None
>
> Return:
> Portfolio UUID

### /portfolios/copy|instantiate/null?sourcecode={sourcecode}&targetcode={targetcode}
POST: Copie brut d'un portfolio sans évaluation de droits
> Parameters:
> None
>
> Return:
> Portfolio UUID
