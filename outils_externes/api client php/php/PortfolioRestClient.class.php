<?php

class PortfolioRestClient {

	//var $url_base = "http://tanami.upmf-grenoble.fr:8080/IUT2-BACKEND/";
	var $url_base = "http://127.0.0.1:8080/Portfolio/rest/api/";
	// OBSOLETE
	var $path_cookie = 'c:/WINDOWS/Temp/portfolio_tmp_cookie.txt';//crée un fichier pour garder les informations du cookie
	var $cookie_user;
	var $cookie_credential;
	var $http_headers = array();
	
	public function __construct($user = null, $credential = null) {
			if($user==null && $credential==null)
				$this->dbConnect();//initialise la connexion à la base de donnees
			else
			{
				$this->cookie_user = $user;
				$this->cookie_credential = $credential;
				$this->http_headers[] = $header_cookie_string = "Cookie: user=".$this->cookie_user.";credential=".$this->cookie_credential;
			}
		}
		
	private function dbConnect() {
	
	    $login = "et1";
		$password = "et1";
		//Connexion à la base de donnees
		
		$lien_connexion = $this->url_base.'credential/login/'.$login.'/password/'.$password;
		
		
		$postfields = array(
			'login' => $login,
			'password' => $password,
		);
		
		
		if (!file_exists(realpath($this->path_cookie))) touch($this->path_cookie);
		
		$curl = curl_init();
		curl_setopt($curl, CURLOPT_URL, $lien_connexion);
		curl_setopt($curl, CURLOPT_COOKIESESSION, true);
		curl_setopt($curl, CURLOPT_RETURNTRANSFER, false);
		curl_setopt($curl, CURLOPT_POST, true);//definit la methode comme un post
		curl_setopt($curl, CURLOPT_POSTFIELDS, $postfields);//envoie les donnees du post
		curl_setopt($curl, CURLOPT_COOKIEJAR, realpath($this->path_cookie));//on recupere les infos du cookie et on le crée
		$return = curl_exec($curl);//execute la requete
		curl_close($curl);//ferme la connexion	
		//unlink($path_cookie);//efface le fichier contenant les cookies	
	
	}
	

	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////			
		
	public  function PostUser($uid, $firstname, $lastname, $email) {
				
		//Creer un utilisateur
		$lien_PostUser = $this->url_base.'users/';
		
		$label = null;
		$password = md5(date("H:i"));
		
		$xml ='<users><user><username>'.$uid.'</username><password>'.$password.'</password><firstname>'.$firstname.'</firstname><lastname>'.$lastname.'</lastname><email>'.$email.'</email></user></users>';
		
		$curl = curl_init();//Initialise une sessions curl
		curl_setopt($curl, CURLOPT_URL, $lien_PostUser);//L'url à recuperer
		curl_setopt($curl, CURLOPT_COOKIESESSION, true); //Demarre un nouvel cookie session
		curl_setopt($curl, CURLOPT_RETURNTRANSFER, true); //recupere le resultat du transfert
		curl_setopt($curl, CURLOPT_POST, true);//definit la methode comme un post
		curl_setopt($curl, CURLOPT_POSTFIELDS, $xml);//envoie les donnees du post
		//curl_setopt($process, CURLOPT_HTTPHEADER, "Cookie: user=".$this->cookie_user.";credential=".$this->cookie_credential); 
		curl_setopt($curl, CURLOPT_COOKIEFILE, realpath($this->path_cookie));//on recupere les infos du cookie et on le crée
		$return = curl_exec($curl);//recupere la source de la page
		curl_close($curl);//ferme la connexion
		//$xml = simplexml_load_string($return);//transformation du xml en tableau
		$nombre = 0;
		if ($return != null){
		//print_r ($return);
		$chaine = $return;
		preg_match_all('#[0-9]+#',$chaine,$extract);
		$nombre = $extract[0][0];
		//print($nombre); 
		}
		

	return $nombre;
	}	  
	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////		
// Relis un model de Portfolio à un utilisateur
	public  function PostPortfolioUser($modelPortfolio, $user) {
				
		//Creer un utilisateur
		$lien_PostPortfolioUser = $this->url_base.'portfolios?user='.$user.'&model='.$modelPortfolio.'';
				
		
		$postfields = array(
				'user' => $user,
				'model' => $modelPortfolio,
			);
		$headers = array(
    'Content-type: application/xml',
    );
		$curl = curl_init();//Initialise une sessions curl
		curl_setopt($curl, CURLOPT_URL, $lien_PostPortfolioUser);//L'url à recuperer
		curl_setopt($curl, CURLOPT_COOKIESESSION, true); //Demarre un nouvel cookie session
		curl_setopt($curl, CURLOPT_RETURNTRANSFER, true); //recupere le resultat du transfert
		curl_setopt($curl, CURLOPT_POST, true);//definit la methode comme un post
		curl_setopt($curl, CURLOPT_POSTFIELDS, $postfields);//envoie les donnees du post
		//curl_setopt($process, CURLOPT_HTTPHEADER, "Cookie: user=".$this->cookie_user.";credential=".$this->cookie_credential); 
		curl_setopt($curl, CURLOPT_HTTPHEADER, $headers); 
		curl_setopt($curl, CURLOPT_COOKIEFILE, realpath($this->path_cookie));//on recupere les infos du cookie et on le crée
		$return = curl_exec($curl);//recupere la source de la page
		curl_close($curl);//ferme la connexion
		//$xml = simplexml_load_string($return);//transformation du xml en tableau
		
		if ($return != null){
		print_r ($return);
		$doc = new DOMDocument();		
			if($doc->loadXML($return)) {
			
			}else{
			
			echo " ";
			
			}
		}
		
		$user = $doc->getElementsByTagName("portfolio");
		
		  foreach($user as $u)
		  {
			if ($u->hasAttribute("id")) {
			$uuid = $u->getAttribute("id");
			}
		  }

	
	return $uuid;
	}	  
	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////	  		
// Relis un model de Portfolio à un utilisateur
	public  function PostRoleUser($grid, $user) {
				
		//Creer un utilisateur
		$lien_PostRoleUser = $this->url_base.'roleUser?grid='.$grid.'&user-id='.$user.'';
				
		
		$postfields = array(
				'grid' => $grid,
				'user-id' => $user,
			);
		
		$curl = curl_init();//Initialise une sessions curl
		curl_setopt($curl, CURLOPT_URL, $lien_PostRoleUser);//L'url à recuperer
		curl_setopt($curl, CURLOPT_COOKIESESSION, true); //Demarre un nouvel cookie session
		curl_setopt($curl, CURLOPT_RETURNTRANSFER, true); //recupere le resultat du transfert
		curl_setopt($curl, CURLOPT_POST, true);//definit la methode comme un post
		curl_setopt($curl, CURLOPT_POSTFIELDS, $postfields);//envoie les donnees du post
		//curl_setopt($process, CURLOPT_HTTPHEADER, "Cookie: user=".$this->cookie_user.";credential=".$this->cookie_credential); 
		curl_setopt($curl, CURLOPT_COOKIEFILE, realpath($this->path_cookie));//on recupere les infos du cookie et on le crée
		$return = curl_exec($curl);//recupere la source de la page
		curl_close($curl);//ferme la connexion
		//$xml = simplexml_load_string($return);//transformation du xml en tableau
		
		//if ($return != null){
		//print_r ($return);
		//$doc = new DOMDocument();		
			//if($doc->loadXML($return)) {
			
			//}else{
			
			//echo " ";
			
			//}
		//}
		
		//$user = $doc->getElementsByTagName("user");
		
		  //foreach($user as $u)
		  //{
			//if ($u->hasAttribute("uuid")) {
			//$uuid = $u->getAttribute("uuid");

			//print_r ($uuid);
			//}
		  //}

	
	return "fini";
	}	  
	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////	 
// Relis un model de Portfolio à un utilisateur
	public  function getRolePortfolio($role, $portfolioId) {
				
		//Creer un utilisateur
		$lien_getRolePortfolio = $this->url_base."rolePortfolio?role=$role&portfolioId=$portfolioId";
				
		

		
		$curl = curl_init();//Initialise une sessions curl
		curl_setopt($curl, CURLOPT_URL, $lien_getRolePortfolio);//L'url à recuperer
		curl_setopt($curl, CURLOPT_COOKIESESSION, true); //Demarre un nouvel cookie session
		curl_setopt($curl, CURLOPT_RETURNTRANSFER, true); //recupere le resultat du transfert
		//curl_setopt($curl, CURLOPT_POST, true);//definit la methode comme un post
		//curl_setopt($curl, CURLOPT_POSTFIELDS, $postfields);//envoie les donnees du post
		//curl_setopt($process, CURLOPT_HTTPHEADER, "Cookie: user=".$this->cookie_user.";credential=".$this->cookie_credential); 
		curl_setopt($curl, CURLOPT_COOKIEFILE, realpath($this->path_cookie));//on recupere les infos du cookie et on le crée
		$return = curl_exec($curl);//recupere la source de la page
		curl_close($curl);//ferme la connexion
		//$xml = simplexml_load_string($return);//transformation du xml en tableau
		
		$nombre = 0;
		if ($return != null){
		//print_r ($return);
		$chaine = $return;
		preg_match_all('#[0-9]+#',$chaine,$extract);
		$nombre = $extract[0][0];
		//print($nombre); 
		}
	
	return $nombre;
	}	  
	
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////	 
		
	public  function  PostGroupsUsers($uuid, $gidgroupe) {
		//associe l'utilisateur à un groupe
		
		/*$gid = array(); 
		 $gid[] = 1;
		 $gid[] = 2;
		 $gid[] = 3;
		 $gid[] = 4; */
			
		//foreach ($gid as $g){ //on loop sur les groupes 
			
			$lien_groupsusers = $this->url_base.'rest/api/groupsUsers?userId='.$uuid.'&groupId='.$gidgroupe.'';
		
			$postfields = array(
				'groupId' => $gidgroupe,
				'userId' => $uuid,
			);
			
			$curl = curl_init();//Initialise une sessions curl
			curl_setopt($curl, CURLOPT_URL, $lien_groupsusers);//L'url à recuperer
			curl_setopt($curl, CURLOPT_COOKIESESSION, true); //Demarre un nouvel cookie session
			curl_setopt($curl, CURLOPT_RETURNTRANSFER, true); //recupere le resultat du transfert
			curl_setopt($curl, CURLOPT_POST, true);//definit la methode comme un post
			curl_setopt($curl, CURLOPT_POSTFIELDS, $postfields);//envoie les donnees du post
			curl_setopt($process, CURLOPT_HTTPHEADER, "Cookie: user=".$this->cookie_user.";credential=".$this->cookie_credential); 
			//curl_setopt($curl, CURLOPT_COOKIEFILE, realpath($this->path_cookie));//on recupere les infos du cookie et on le crée
			$return = curl_exec($curl);//recupere la source de la page
			curl_close($curl);//ferme la connexion		
		//}
		

	
	}	
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

public  function getNode($uuid) {
				
		//Creer un utilisateur
		$lien_getNode = $this->url_base."rest/api/nodes/$uuid/children";
		
		
		
		$curl = curl_init();//Initialise une sessions curl
		curl_setopt($curl, CURLOPT_URL, $lien_getNode);//L'url à recuperer
		curl_setopt($curl, CURLOPT_COOKIESESSION, true); //Demarre un nouvel cookie session
		curl_setopt($curl, CURLOPT_RETURNTRANSFER, true); //recupere le resultat du transfert
		//curl_setopt($curl, CURLOPT_POST, true);//definit la methode comme un post
		//curl_setopt($curl, CURLOPT_POSTFIELDS, $xml);//envoie les donnees du post
		//print_r($this->http_headers);
		//exit();
		curl_setopt($curl, CURLOPT_HTTPHEADER, $this->http_headers ); 
		//curl_setopt($curl, CURLOPT_COOKIEFILE, realpath($this->path_cookie));//on recupere les infos du cookie et on le crée
		$return = curl_exec($curl);//recupere la source de la page
	
		curl_close($curl);//ferme la connexion
		//$xml = simplexml_load_string($return);//transformation du xml en tableau
		
		

	
		return $return;
	}
	

////////////////////

	public function getNodeBySemanticTag($portfolio_uuid, $semantic_tag) {
				
		//Creer un utilisateur
		$lien_getNode = $this->url_base."rest/api/nodes/bysemantictag/".$portfolio_uuid."/".$semantic_tag;
		
		
		
		$curl = curl_init();//Initialise une sessions curl
		curl_setopt($curl, CURLOPT_URL, $lien_getNode);//L'url à recuperer
		curl_setopt($curl, CURLOPT_COOKIESESSION, true); //Demarre un nouvel cookie session
		curl_setopt($curl, CURLOPT_RETURNTRANSFER, true); //recupere le resultat du transfert
		//curl_setopt($curl, CURLOPT_POST, true);//definit la methode comme un post
		//curl_setopt($curl, CURLOPT_POSTFIELDS, $xml);//envoie les donnees du post
		curl_setopt($process, CURLOPT_HTTPHEADER, "Cookie: user=".$this->cookie_user.";credential=".$this->cookie_credential); 
		//curl_setopt($curl, CURLOPT_COOKIEFILE, realpath($this->path_cookie));//on recupere les infos du cookie et on le crée
		$return = curl_exec($curl);//recupere la source de la page
		curl_close($curl);//ferme la connexion
		//$xml = simplexml_load_string($return);//transformation du xml en tableau
		
		

	
		return $return;
	}
	
	
}
?>