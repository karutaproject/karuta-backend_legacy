<?php

$french = array(
    'au_subgroups' => "Sous-Groupes",
    'au_subgroups:subgroup' => "Sous-Groupe",
    'au_subgroups:subgroups' => "Sous-Groupes",
    'au_subgroups:parent' => "Groupe parent",
    'au_subgroups:add:subgroup' => 'Créer un sous-groupe',
    'au_subgroups:nogroups' => 'Pas de sous groupes créés',
    'au_subgroups:error:notparentmember' => "Les utilisateurs ne peuvent pas se joindre à un sous-groupe si ils ne sont pas membres du groupe parent",
    'au_subtypes:error:create:disabled' => "La création de sous-groupe a été désactivé pour ce groupe",
    'au_subgroups:noedit' => "Vous ne pouvez pas modifier ce groupe",
    'au_subgroups:subgroups:delete' => "Effacer le groupe",
    'au_subgroups:delete:label' => "Qu'est-ce qui devrait arriver au contenu de ce groupe ? Toute option choisie sera également applicable à des sous-groupes qui seront supprimés.",
    'au_subgroups:deleteoption:delete' => 'Supprimer tout le contenu de ce groupe',
    'au_subgroups:deleteoption:owner' => 'Transférer tout le contenu aux créateurs originaux',
    'au_subgroups:deleteoption:parent' => 'Transférer tout le contenu au groupe parent',
    'au_subgroups:subgroup:of' => "Sous-Groupe de %s",
    'au_subgroups:setting:display_alphabetically' => "Afficher la liste personnelle des groupes par ordre alphabétique?",
    'au_subgroups:setting:display_subgroups' => 'Voir les sous-groupes dans les listes de groupe Standard?',
    'au_subgroups:setting:display_featured' => 'Voir les groupes en vedette sur les listes personnelles de groupe?',
    'au_subgroups:error:invite' => "Action annulée - les utilisateurs suivants ne sont pas membres du groupe parent et ne peut être invités / ajoutés.",
    'au_subgroups:option:parent:members' => "Membres du groupe parent",
    'au_subgroups:subgroups:more' => "Voir tous les sous-groupes",
    'subgroups:parent:need_join' => "Inscrivez-vous au Groupe Parent",
	
	// group options
	'au_subgroups:group:enable' => "Sous-groupes: Activer les sous-groupes pour ce groupe?",
	'au_subgroups:group:memberspermissions' => "Sous-groupes: Activer la possibilité pour tout membre de créer des sous-groupes ? (si non, seuls les administrateurs du groupe seront en mesure de créer des sous-groupes )",
    
    /*
     * Widget
     */
    'au_subgroups:widget:order' => 'Trier les résultats par',
    'au_subgroups:option:default' => 'Heure de création',
    'au_subgroups:option:alpha' => 'Alphabétique',
    'au_subgroups:widget:numdisplay' => 'Nombre de sous-groupes à afficher',
    'au_subgroups:widget:description' => 'Liste des Sous-groupes de ce groupe',
	
	/*
	 * Move group
	 */
	'au_subgroups:move:edit:title' => "Faire ce groupe un sous-groupe d'un autre groupe",
	'au_subgroups:transfer:help' => "Vous pouvez définir ce groupe comme un sous-groupe d'un autre groupe, vous disposez des autorisations pour modifier . Si les utilisateurs ne sont pas membres du nouveau groupe parent, ils seront retirés de ce groupe. Une invitation sera envoyée afin de les inscrire dans le nouveau groupe de parent et tous les sous-groupes menant à celui-ci. <b>Les sous-groupes de ce groupe seront également transférés /b>",
	'au_subgroups:search:text' => "Rechercher des groupes",
	'au_subgroups:search:noresults' => "Pas de groupe trouvés",
	'au_subgroups:error:timeout' => "Recherche terminée",
	'au_subgroups:error:generic' => "Une erreur est survenue avec la recherche",
	'au_subgroups:move:confirm' => "Etes-vous sûr que vous voulez faire de ce groupe un sous-groupe d'un autre groupe?",
	'au_subgroups:error:permissions' => "Vous devez disposer des autorisations d'édition pour le sous-groupe et de chaque parent jusqu'au sommet. En outre, un groupe ne peut pas passer à un sous-groupe de lui-même.",
	'au_subgroups:move:success' => "Le groupe a été déplacé avec succès",
	'au_subgroups:error:invalid:group' => "Identificateur de groupe non valide",
	'au_subgroups:invite:body' => "Salut %s,

Le groue %s a été déplacé vers un sous-groupe du groupe %s.
Comme vous n'êtes pas actuellement un membre du nouveau groupe parent, vous avez été retiré du sous-groupe. Vous avez été ensuite ré-invité dans le groupe et en acceptant l'invitation vous rejoindrez automatiquement, en tant que membre, tous les groupes parents.
Cliquez ci-dessous pour afficher vos invitations:

%s",

);
					
add_translation("fr",$french);
