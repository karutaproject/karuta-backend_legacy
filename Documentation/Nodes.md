### /nodes/node/{node-id}
GET get info
> Parameters:
> None
>
> Return:
>
	<asmUnit id='54dee475-d9aa-49dd-b9be-066d9181aeb3'>
	<metadata />
	<metadata-wad seenoderoles="all" />
	<asmResource xsi_type="nodeRes">
		<code />
		<label lang="fr">Nouvelle Unité</label>
		<label lang="en">New Unit</label>
		<description />
	</asmResource>
	<asmResource xsi_type="context" />
	<asmUnitStructure id='9f4e75e3-67b0-48b1-b149-1d5edf9be475'>
		<metadata   semantictag='dfg' sharedNode='N' sharedNodeResource='N' />
		<metadata-wad   seenoderoles='all' editnoderoles='' delnoderoles='' commentnoderoles='' annotnoderoles='' editresroles='etudiant' submitroles='' shownoderoles='' showroles='' menuroles="" query='' displaytree='' help="" />
		<asmResource xsi_type='nodeRes'>
			<code></code>
			<label lang='fr'>wdty</label>
			<label lang='en'></label>
			<description></description>
		</asmResource>
		<asmResource xsi_type='context'></asmResource>
		<asmContext id='e1b3b540-24d1-49f6-9707-12b5d9cdc572' resid='4d207a9e-9449-4654-98b8-fb60611f89a7'>
			<metadata   sharedNode='N' sharedNodeResource='N' sharedResource='N' />
			<metadata-wad   seenoderoles='all' editnoderoles='' delnoderoles='' commentnoderoles='' annotnoderoles='' editresroles='' submitroles='' shownoderoles='' showroles='' menuroles="" query='' displaytree='' help="" />
			<asmResource xsi_type='nodeRes'>
				<code></code>
				<label lang='fr'>nom</label>
				<label lang='en'></label>
				<description></description>
			</asmResource>
			<asmResource xsi_type="context" />
			<asmResource id='af890a44-47fa-4f48-9d23-d18e569398fd' contextid='e1b3b540-24d1-49f6-9707-12b5d9cdc572'  modified='2013-06-19 18:21:05.0'  xsi_type='Field'>
				<text></text>
			</asmResource>
		</asmContext>
	</asmUnitStructure>
	</asmUnit>

PUT: Replace node

### /nodes/node/{node-id}/children
GET: node info and children

### /nodes/node/{node-id}/metadata
PUT: <metadata  sharedNode='N' sharedNodeResource='N' /> 

### /nodes/node/{node-id}/metadatawad
GET: node metadata
> Parameters:
> None
>
> Return:
>
	<metadata-wad seenoderoles='etudiant tuteur' editnoderoles='' delnoderoles='' commentnoderoles='' annotnoderoles='' editresroles='' submitroles='' shownoderoles='etudiant' showroles='tuteur@' menuroles="" query='' displaytree='' help="" />

PUT: replace metadata-wad
> Parameters:
> None
>
> Return:
> None
<metadata-wad  seenoderoles='all' editnoderoles='' delnoderoles='' commentnoderoles='etudiant' annotnoderoles='' editresroles='' submitroles='' shownoderoles='' showroles='' menuroles="" query='' displaytree='' help="" /> 


### /nodes/firstbysemantictag/{portfolio-id}/{semantictag}
GET: Query, fetch node via semantic tag in portfolio

### /nodes/bysemantictag/{portfolio-id}/{semantictag}
GET: Query, fetch node via semantic tag in portfolio

### /nodes/node/{nodeid}/nodecontext
PUT: Replace nodecontext
> Parameters:
> None
>
> Return:
> None
<asmResource xsi_type='context'>
    <comment>commentaires</comment>
</asmResource> 

### /nodes/node/{nodeid}/noderesource
PUT: Replace resource
> Parameters:
> None
>
> Return:
> None
<asmResource xsi_type='nodeRes'>
    <code></code>
    <label lang='fr'>Prénom</label>
    <label lang='en'></label>
    <description></description>
</asmResource> 

### /nodes/node/[copy|import]/{dest-id}?srcetag={semantictag}&srcecode={code}
POST: Instancie un noeud et évalue les droits des attributs
> Parameters:
> - {dest-id} = identifiant du noeud parent dans lequel coller l'élément
> - {semantictag} = tag sémantique de l'élément à copier
> - {code} = code du portfolio dans lequel chercher le tag sémantique
>
> Return:
> code du nouvel élément créé.


### /nodes?portfoliocode={code}&semtag={tag}&semtag_parent={parent-tag}&code_parent={parent-code}
GET: récupère des noeuds selon code et tag

### /nodes/node/{parent-id}
POST: Ajoute un noeud
> Parameters:
> None
>
> Return:
> None
<asmContext id='' resid='' ctxid=''>
    <metadata />
    <metadata-wad seenoderoles='all'/>
    <asmResource xsi_type='nodeRes'>
        <code></code>
        <label lang='fr'></label>
        <label lang='en'></label>
        <description></description>
    </asmResource>
    <asmResource xsi_type='context'></asmResource>
    <asmResource xsi_type='Calendar'><text></text>
    </asmResource>
</asmContext> 

<node id='sdkjjd-sdkso kkqokas235rkd83kjd-sje8' />

DELETE: Efface le noeud et enfants
> Parameters:
> None
>
> Return:
> None

### /nodes/node/{node-id}/moveup
POST: Monte le noeud d'une place
> Parameters:
> None
>
> Return:
> None

### /nodes/node/{node-id}/parentof/{parent-id}
POST: Bouge le noeud sous un autre parent
> Parameters:
> None
>
> Return:
> None


### /nodes/node/{node-id}/action/[show|hide|submit]
POST: Execute l'action au noeud
> Parameters:
> None
>
> Return:
> None

### /nodes/{node-id}/xsl/{xsl-filename}
GET: fetch processed node

### /nodes/{node-id}/frommodelbysemantictag/{semantic-tag}
POST
