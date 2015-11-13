<?php

$french = array(
	'thewire_tools' => "Outils du Mur des publications",
	'thewire_tools:no_result' => "Pas de publications trouvées",
	'thewire_tools:login_required' => "Vous devez être connecté pour utiliser cette fonctionnalité",
	'admin:upgrades:thewire_tools_mentions' => "Mentions dans le mur des publications",
	
	// menu
	'thewire_tools:menu:mentions' => "Mentions",

	// settings
	'thewire_tools:settings:enable_group' => "Activer Le mur des publications pour les groupes",
	'thewire_tools:settings:extend_widgets' => "Étendre le widget du mur des publications avec la possibilité de poster directement depuis le widget",
	'thewire_tools:settings:extend_activity' => "Elargir la page d' activité avec le formulaire du mur des publications",
	'thewire_tools:settings:wire_length' => "Réglez la longueur de max d'une publication",
	'thewire_tools:settings:mention_display' => "Comment afficher les mentions dans une publication",
	'thewire_tools:settings:mention_display:username' => "@username",
	'thewire_tools:settings:mention_display:displayname' => "@displayname",
	
	'thewire_tools:settings:description' => "Recevoir une notification lorsque vous êtes mentionné dans une publication du mur",
	
	// notification
	// mention
	'thewire_tools:notify:mention:subject' => "Vous avez été mentionné sur mur des publications",
	'thewire_tools:notify:mention:message' => "Salut %s,

%s vous a mentionné dans sa publication.

Cliquer ici pour voir les mentions qui vous concernent:
%s",

	// user settings
	'thewire_tools:usersettings:notify_mention' => "Je souhaite recevoir une notification lorsque je suis mentionné",
	
	// group wire
	'thewire_tools:group:title' => "Mur des publications du groupe",
	'thewire_tools:groups:tool_option' => "Permettre le mur des publications dans le groupe",
	'thewire_tools:groups:error:not_enabled' => "Le mur des publications a été désactivé pour ce groupe",
	'thewire_tools:groups:mentions' => "Les mentions sont réservées uniquement aux membres du groupe",
	
	// search
	'thewire_tools:search:title' => "Rechercher dans les publications du mur: '%s'",
	'thewire_tools:search:title:no_query' => "Rechercher dans le mur des publications",
	'thewire_tools:search:no_query' => "Pour rechercher dans le mur des publications, please enter your search text above",
	
	// reshare
	'thewire_tools:reshare' => "Partager dans le mur des publications",
	'thewire_tools:reshare:count' => "Voir qui a partagé ça",
	'thewire_tools:reshare:source' => "Source",
	'thewire_tools:reshare:list' => "%s a partagé ça %s",
	
	// widget
	// thewire_groups
	'widgets:thewire_groups:title' => "Mur de publication du groupe",
	'widgets:thewire_groups:description' => "Afficher le mur des publications dans le groupe",
	
	// index_thewire
	'widgets:index_thewire:title' => "Mur des publications",
	'widgets:index_thewire:description' => "Afficher les dernières publications du mur de votre communauté",
	
	// the wire post
	'widgets:thewire_post:title' => "Mettre à jour le mur des publications",
	'widgets:thewire_post:description' => "Mettre à jour votre statut sur le mur des publications par ce widget",

	// the wire (default widget)
	'widgets:thewire:owner' => "De qui afficher les publications du mur",
	'widgets:thewire:filter' => "Filtrer les publications du mur (facultatif)",
		
);

add_translation("fr", $french);
