<?php

	require_once("PortfolioRestClient.class.php");
	
	$array = array('astierfl', 'astier', 'fl');
	$count = count($array);
	
	$instance = new PortfolioRestClient;
	
	for ($i = 0; $i < 1; $i++) {
		$userId = $instance->PostUser($array[$i], "lalal", "lalal", "lalal");
		print($userId);
		$portfolioId = $instance->PostPortfolioUser('64598787d5a1466f9d828ded2a4828a9',$userId);
		print($portfolioId);
		$grid = $instance->getRolePortfolio('etudiant','64598787d5a1466f9d828ded2a4828a9');
		print_r($grid);
		$resultat = $instance->PostRoleUser($grid,$userId);
		print($resultat);
	}


	//CreerCompteUtilisateur(202819, 1);
	
?>