### /resources/resource/{context-id}
GET: Ressource d'un noeud
> Parameters:
> None
>
> Return:
> None
PUT: Replace ressource d'un noeud
> Parameters:
> None
>
> Return:
>
	<asmResource xsi_type='Calendar'>
		<text>2013/06/15</text>
	</asmResource> 

### /resources/resource/{context-id}[?userrole={userrole}]
PUT: Ressource d'un noeud
> Parameters:
> None
>
	0 or 1

### /resources/resource/file/{contextid}?lang={langcode}
GET:
> Parameters:
> None
>
> Return:
> File

POST:
> Data sent:
> Form POST with enctype="multipart/form-data", input type file named "uploadfile"
>
> Return:
>	JSON following blueimp's JQuery-File-Upload format
>
	{"files": [
		{
			"name": "picture1.jpg",
			"size": 902604,
			"url": "http:\/\/example.org\/files\/picture1.jpg",
			"thumbnailUrl": "http:\/\/example.org\/files\/thumbnail\/picture1.jpg",
			"deleteUrl": "http:\/\/example.org\/files\/picture1.jpg",
			"deleteType": "DELETE"
		},
		{
			"name": "picture2.jpg",
			"size": 841946,
			"url": "http:\/\/example.org\/files\/picture2.jpg",
			"thumbnailUrl": "http:\/\/example.org\/files\/thumbnail\/picture2.jpg",
			"deleteUrl": "http:\/\/example.org\/files\/picture2.jpg",
			"deleteType": "DELETE"
		}
	]}
