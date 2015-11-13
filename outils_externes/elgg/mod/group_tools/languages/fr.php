<?php

$french = array(

	// general
	'group_tools:decline' => "Refuser",
	'group_tools:revoke' => "Revoquer",
	'group_tools:add_users' => "Ajouter des utilisateurs",
	'group_tools:in' => "dans",
	'group_tools:remove' => "Enlever",
	'group_tools:delete_selected' => "Supprimer la sélection",
	'group_tools:clear_selection' => "Effacer la sélection",
	'group_tools:all_members' => "Tous les membres",
	'group_tools:explain' => "Explications",

	'group_tools:default:access:group' => "Membres du groupe uniquement",

	'group_tools:joinrequest:already' => "Révoquer la demande d'adhésion",
	'group_tools:joinrequest:already:tooltip' => "Vous avez déjà demandé à rejoindre ce groupe, cliquez ici pour révoquer cette demande",
	'group_tools:join:already:tooltip' => "Vous avez été invité à ce groupe et vous pouvez donc vous inscrire dès maintenant.",

	// menu
	'group_tools:menu:mail' => "Envoyer un courriel aux membres",
	'group_tools:menu:invitations' => "Gérer les invitations",
	'admin:administer_utilities:group_bulk_delete' => "Group en vrac à supprimer",

	'admin:appearance:group_tool_presets' => "Outils de groupe prédéfinis",
	
	// plugin settings
	'group_tools:settings:invite:title' => "Options d'invitation de groupe",
	'group_tools:settings:management:title' => "Options générales de groupe",
	'group_tools:settings:default_access:title' => "Accès par défaut au groupe",

	'group_tools:settings:admin_transfer' => "Permettre de transférer la propriété du groupe",
	'group_tools:settings:admin_transfer:admin' => "Administrateur du site uniquement",
	'group_tools:settings:admin_transfer:owner' => "Les propriétaires de groupes et les administrateurs du site",

	'group_tools:settings:multiple_admin' => "Autoriser plusieurs administrateurs du groupe",
	'group_tools:settings:auto_suggest_groups' => "Auto suggérer des groupes dans la page des groupes 'Suggérés' basée sur les informations du profil. Sera complété par les groupes proposés prédéfinis. Paramétrer ceci à 'Non' n'affichera que les groupes suggérés préféfinis (si il y en a plusieurs).",

	'group_tools:settings:invite' => "Autoriser tous les utilisateurs à être invités ( pas seulement les amis )",
	'group_tools:settings:invite_email' => "Autoriser tous les utilisateurs à être invités par adresse e-mail",
	'group_tools:settings:invite_csv' => "Autoriser tous les utilisateurs à être invités par fichier CSV",
	'group_tools:settings:invite_members' => "Permettre aux membres du groupe d'inviter de nouveaux utilisateurs",
	'group_tools:settings:invite_members:default_off' => "Oui, désactivé par défaut",
	'group_tools:settings:invite_members:default_on' => "Oui, activé par défaut",
	'group_tools:settings:invite_members:description' => "Les propriétaires de groupes/ administrateurs peuvent l'activer ou le désactiver pour leur groupe",
	'group_tools:settings:domain_based' => "Activer les groupes basés sur le domaine",
	'group_tools:settings:domain_based:description' => "Les utilisateurs peuvent rejoindre un groupe en fonction de leur domaine de messagerie. Lors de l'inscription, ils rejoindront automatiquement des groupes en fonction de leur domaine de messagerie.",

	'group_tools:settings:mail' => "Autoriser le courrier de groupe (les administrateurs du groupe pourront envoyer un message à tous les membres du groupe",

	'group_tools:settings:listing:default' => "Liste des onglets de groupe par défaut",
	'group_tools:settings:listing:available' => "Liste des onglets de groupe disponibles",

	'group_tools:settings:default_access' => "Quel devrait être l'accès par défaut aux contenus dans les groupes de ce site",
	'group_tools:settings:default_access:disclaimer' => "<b>AVERTISSEMENT:</b> cela ne fonctionnera pas à moins que vous ayez <a href='https://github.com/Elgg/Elgg/pull/253' target='_blank'>https://github.com/Elgg/Elgg/pull/253</a> appliquée à votre installation Elgg.",

	'group_tools:settings:search_index' => "Permettre aux groupes fermés d' être indexés par les moteurs de recherche",
	'group_tools:settings:auto_notification' => "Activer automatiquement une notification de groupe quand quelq'un rejoint un groupe",
	'group_tools:settings:show_membership_mode' => "Montrer le statut de membre ouvert / fermé sur le profil du groupe et le bloc du propriétaire",
	'group_tools:settings:show_hidden_group_indicator' => "Afficher un indicateur si un groupe est caché",
	'group_tools:settings:show_hidden_group_indicator:group_acl' => "Oui, si le groupe est : membres seulement",
	'group_tools:settings:show_hidden_group_indicator:logged_in' => "Oui, pour tous les groupes non publics",
	
	'group_tools:settings:special_states' => "Groupes avec un état spécial",
	'group_tools:settings:special_states:featured' => "Sélectioné",
	'group_tools:settings:special_states:featured:description' => "Les administrateurs du site ont choisi de présenter les groupes suivants.",
	'group_tools:settings:special_states:auto_join' => "Rejoindre automatiquement",
	'group_tools:settings:special_states:auto_join:description' => "Les nouveaux utilisateurs rejoindront automatiquement les groupes suivants.",
	'group_tools:settings:special_states:suggested' => "Suggérés",
	'group_tools:settings:special_states:suggested:description' => "Les groupes suivants sont suggérés pour les (nouveaux) utilisateurs. Il est possible d'auto-suggérer des groupes, si aucun des groupes sont automatiquement détectés ou trop peu, la liste sera annexée par ces groupes.",

	'group_tools:settings:fix:title' => "Résoudre les problèmes d'accès au groupe",
	'group_tools:settings:fix:missing' => "Il y a %d utilisateurs qui sont membres du groupe, mais qui n'ont pas accès à du contenu partagé avec le groupe.",
	'group_tools:settings:fix:excess' => "Il y a %d utilisateurs qui ont accès à un contenu de groupe dans des groupes dans lesquels ils ne sont plus membres.",
	'group_tools:settings:fix:without' => "Il y a %d groupes sans la possibilité de partager du contenu avec leurs membres.",
	'group_tools:settings:fix:all:description' => "Résoudre tous les problèmes ci-dessus à la fois.",
	'group_tools:settings:fix_it' => "Résoudre ça",
	'group_tools:settings:fix:all' => "Résoudre tous les problèmes",
	'group_tools:settings:fix:nothing' => "Tout va bien avec les groupes de votre site!",

	'group_tools:settings:member_export' => "Autoriser les administrateurs du groupe exporter des informations de membre",
	'group_tools:settings:member_export:description' => "Cela comprend le nom, l'adresse, le nom et l'e-mail de l'utilisateur.",
	
	// group tool presets
	'group_tools:admin:group_tool_presets:description' => "Ici vous pouvez configurer les outils prédéfinis du groupe.
Lorsqu'un utilisateur crée un groupe, il / elle peut choisir un ou des préréglages afin d'obtenir rapidement les outils appropriés. Une option vide est également offerte à l'utilisateur pour lui permettre de faire ses / ses propres choix.",
	'group_tools:admin:group_tool_presets:header' => "Paramètres préféfinis existants",
	'group_tools:create_group:tool_presets:description' => "Vous pouvez sélectionner un outil de groupe prédéfini ici . Si vous le faites , vous obtiendrez un ensemble d'outils qui sont configurés pour le préréglage sélectionné . Vous pouvez toujours choisir d'ajouter des outils supplémentaires pour une présélection, ou supprimer ceux que vous ne souhaitez pas.",
	'group_tools:create_group:tool_presets:active_header' => "Outil pour ce réglage prédéfini",
	'group_tools:create_group:tool_presets:more_header' => "Outils supplémentaires",
	'group_tools:create_group:tool_presets:select' => "Sélection un type de groupe",
	'group_tools:create_group:tool_presets:show_more' => "Plus d'outils",
	'group_tools:create_group:tool_presets:blank:title' => "Groupe vide",
	'group_tools:create_group:tool_presets:blank:description' => "Choisir ce groupe pour sélectionner vos propres outils.",
	
	
	// group invite message
	'group_tools:groups:invite:body' => "Bonjour %s,

%s vous a invité à rejoindre le '%s' groupe.
%s

Cliquez ci-dessous pour afficher vos invitations:
%s",

	// group add message
	'group_tools:groups:invite:add:subject' => "Vous avez été ajouté au groupe %s",
	'group_tools:groups:invite:add:body' => "Bonjour %s,

%s vous a ajouté au groupe %s.
%s

Pour voir le groupe cliquez sur le lien
%s",
	// group invite by email
	'group_tools:groups:invite:email:subject' => "Vous avez été invité pour le groupe %s",
	'group_tools:groups:invite:email:body' => "Bonjour,

%s vous a invité à rejoindre le groupe %s sur %s.
%s

Si vous n'avez pas de compte sur %s svp enregistrez-vous sur
%s

Si vous avez déjà un compte, ou après vous être inscrit, s'il vous plaît cliquer sur le lien suivant pour accepter l'invitation
%s

Vous pouvez aussi aller à : tous les groupes de site - > invitations Groupe et entrez le code suivant:
%s",
	// group transfer notification
	'group_tools:notify:transfer:subject' => "L'administration du goupe %s vous a été confiée",
	'group_tools:notify:transfer:message' => "Bonjour %s,

%s vous a designé en tant qu'administrateur du groupe %s.

Pour visiter le groupe s'il vous plaît cliquer sur le lien suivant:
%s",
	
	// deline membeship request notification
	'group_tools:notify:membership:declined:subject' => "Votre demande d'adhésion pour le groupe '%s' a été refusée",
	'group_tools:notify:membership:declined:message' => "Bonjour %s,

Votre demande d'adhésion pour le groupe '%s' a été refusée.

You can find the group here:
%s",
	'group_tools:notify:membership:declined:message:reason' => "Bonjour %s,

Votre demande d'adhésion pour le groupe '%s' a été refusée, par ce que:

%s

Vous pouvez trouver le groupe ici:
%s",

	// group edit tabbed
	'group_tools:group:edit:profile' => "Profil",
	'group_tools:group:edit:access' => "Accès",
	'group_tools:group:edit:tools' => "Outils",
	'group_tools:group:edit:other' => "Autres options",

	// admin transfer - form
	'group_tools:admin_transfer:current' => "Conserver le propriétaire actuel: %s",
	'group_tools:admin_transfer:myself' => "Moi-même",
	'group_tools:admin_transfer:submit' => "Transférer",
	
	// special states form
	'group_tools:special_states:title' => "Etats spéciaux de groupes",
	'group_tools:special_states:description' => "Un groupe peut avoir plusieurs états spéciaux , voici un aperçu des états spéciaux et leur valeur actuelle.",
	'group_tools:special_states:featured' => "Est ce groupe est sélectionné ou en vedette",
	'group_tools:special_states:auto_join' => "Est-ce que les utilisateurs vont rejoindre automatiquement ce groupe",
	'group_tools:special_states:auto_join:fix' => "Pour rendre tous les membres du site membres de ce groupe, s'il vous plaît %scliquez ici%s.",
	'group_tools:special_states:suggested' => "Est-ce groupe est suggéré aux (nouveaux) utilisateurs",
	
	// group admins
	'group_tools:multiple_admin:group_admins' => "Administrateurs du groupe",
	'group_tools:multiple_admin:profile_actions:remove' => "supprimer des administrateurs du groupe",
	'group_tools:multiple_admin:profile_actions:add' => "Ajouter un administrateur au groupe",

	'group_tools:multiple_admin:group_tool_option' => "Permettre aux administrateurs du groupe d'assigner d'autres administrateurs",

	// cleanup options
	'group_tools:cleanup:title' => "Nettoyage de la colonne latérale du groupe",
	'group_tools:cleanup:description' => "Nettoyer la colonne latérale du groupe nettoyée. Cela n'aura aucun effets pour les administrateurs du groupe.",
	'group_tools:cleanup:owner_block' => "Limitez le bloc de propriétaire",
	'group_tools:cleanup:owner_block:explain' => "Le bloc de propriétaire peut être trouvé au sommet de la barre latérale , quelques liens supplémentaires peuvent être affichés dans cette zone ( exemple: liens RSS ).",
	'group_tools:cleanup:actions' => "Masquer le bouton de demande d'adhésion ou de rejoindre le groupe",
	'group_tools:cleanup:actions:explain' => "En fonction de votre groupe, les utilisateurs peuvent rejoindre directement le groupe envoyer une demande.",
	'group_tools:cleanup:menu' => "Masquer le menu latéral des éléments",
	'group_tools:cleanup:menu:explain' => "Cacher les liens du menu vers les différents outils du groupe. Les utilisateurs ne pourront avoir accès aux outils du groupe en utilisant les widgets du groupe.",
	'group_tools:cleanup:members' => "Cacher les membres du groupe",
	'group_tools:cleanup:members:explain' => "Sur la page de profil du groupe une liste des membres du groupe peut être trouvée dans la section en surbrillance. Vous pouvez choisir de masquer cette liste.",
	'group_tools:cleanup:search' => "Masquer la recherche dans le groupe",
	'group_tools:cleanup:search:explain' => "Sur la page de profil de groupe une boîte de recherche est disponible . Vous pouvez choisir de la masquer.",
	'group_tools:cleanup:featured' => "Voir les groupes sélectionnés ou en vedette dans la colonne latérale",
	'group_tools:cleanup:featured:explain' => "Vous pouvez choisir d'afficher une liste de groupes en vedette dans la section en surbrillance sur la page de profil du groupe",
	'group_tools:cleanup:featured_sorting' => "Comment trier les groupes sélectionnés ou en vedette",
	'group_tools:cleanup:featured_sorting:time_created' => "Les nouveaux en premier",
	'group_tools:cleanup:featured_sorting:alphabetical' => "Alphabétique",
	'group_tools:cleanup:my_status' => "Cacher la colonne latérale de mon status ",
	'group_tools:cleanup:my_status:explain' => "Dans la colonne latérale sur la page de profil de groupe il ya un élément qui affiche votre statut de membre actuel et d'autres informations d'état. Vous pouvez choisir de cacher ces informations.",

	// group default access
	'group_tools:default_access:title' => "Accès au groupe par défaut",
	'group_tools:default_access:description' => "Ici vous pouvez contrôler comment l'accès par défaut à un nouveau contenu dans votre groupe devrait être.",

	// group notification
	'group_tools:notifications:title' => "Notifications de groupe",
	'group_tools:notifications:description' => "Ce groupe a %s membres, qui parmi eux %s ont activé les notifications sur les activités dans ce groupe. Ci-dessous vous pouvez changer cela pour tous les utilisateurs du groupe.",
	'group_tools:notifications:disclaimer' => "Avec de grands groupes cela pourrait prendre un certain temps.",
	'group_tools:notifications:enable' => "Activer les notifications pour tous",
	'group_tools:notifications:disable' => "Désactiver les notifications pour tous",

	// group profile widgets
	'group_tools:profile_widgets:title' => "Montrer le widget de profil du groupe à des non membres",
	'group_tools:profile_widgets:description' => "Ceci est un groupe fermé. Par défaut Aucuns widgets sont affichés pour les non-membres. Ici vous pouvez changer cela si vous le souhaitez.",
	'group_tools:profile_widgets:option' => "Autoriser les non membres à afficher des widgets sur la page de profil de groupe:",

	// group mail
	'group_tools:mail:message:from' => "De Groupe",

	'group_tools:mail:title' => "Envoyer un courriel aux membres du groupe",
	'group_tools:mail:form:recipients' => "Nombre de destinataires",
	'group_tools:mail:form:members:selection' => "Sélectionner des membres",

	'group_tools:mail:form:title' => "Sujet",
	'group_tools:mail:form:description' => "Corps",

	'group_tools:mail:form:js:members' => "S'il vous plaît sélectionner au moins un membre pour envoyer le message à",
	'group_tools:mail:form:js:description' => "SVP saisissez un message",

	// group invite
	'group_tools:groups:invite:title' => "Inviter des utilisateurs dans ce groupe",
	'group_tools:groups:invite' => "Inviter des utilisateurs",

	'group_tools:group:invite:friends:select_all' => "Sélectionner tous les amis",
	'group_tools:group:invite:friends:deselect_all' => "Déselectionner tous amis",

	'group_tools:group:invite:users' => "Trouver des utilisateurs",
	'group_tools:group:invite:users:description' => "Entrez un nom un pseudo d'un membre du site et sélectionnez le/ la dans la liste",
	'group_tools:group:invite:users:all' => "Inviter tous les membres du site dans ce groupe",

	'group_tools:group:invite:email' => "Utiliser l'adresse e-mail",
	'group_tools:group:invite:email:description' => "Saisissez une adresse e-mail valide et sélectionner-la dans la liste",

	'group_tools:group:invite:csv' => "Utiliser l'upload par fichier CSV",
	'group_tools:group:invite:csv:description' => "Vous pouvez uploader un fichier CSV avec les utilisateurs à inviter.<br />Le format doit être: nom affiché dans le site;adresse e-mail. Ca ne devrait pas être une ligne d'en-tête.",

	'group_tools:group:invite:text' => "Note personnelle (facultatif )",
	'group_tools:group:invite:add:confirm' => "Etes-vous sûr que vous voulez ajouter ces utilisateurs directement?",

	'group_tools:group:invite:resend' => "Renvoyer des invitations à des utilisateurs qui ont déjà été invités",

	'group_tools:groups:invitation:code:title' => "Invitation du Groupe par e-mail",
	'group_tools:groups:invitation:code:description' => "Si vous avez reçu une invitation à rejoindre un groupe par e-mail, vous pouvez entrer le code d'invitation ici pour accepter l'invitation. Si vous cliquez sur le lien dans l'invitation par e-mail le code sera entré directement pour vous.",

	// group membership requests
	'group_tools:groups:membershipreq:requests' => "Demandes d'adhésion",
	'group_tools:groups:membershipreq:invitations' => "Utilisateurs invités",
	'group_tools:groups:membershipreq:invitations:none' => "Aucune invitation d'utilisateurs en attente",
	'group_tools:groups:membershipreq:email_invitations' => "Adresses e-mail des invités",
	'group_tools:groups:membershipreq:email_invitations:none' => "Aucune invitation par e-mail en attente",
	'group_tools:groups:membershipreq:invitations:revoke:confirm' => "Êtes-vous sûr de vouloir annuler cette invitation",
	'group_tools:groups:membershipreq:kill_request:prompt' => "Etes-vous sûr que vous souhaitez décliner cette demande d'adhésion ? Vous pouvez dire à l'utilisateur pourquoi vous avez refusé la demande.",

	// group invitations
	'group_tools:group:invitations:request' => "Demandes d'adhésion en suspens",
	'group_tools:group:invitations:request:revoke:confirm' => "Etes-vous sûr que vous voulez annuler votre demande d'adhésion ?",
	'group_tools:group:invitations:request:non_found' => "Il n'y a pas de demandes d'adhésion en circulation à ce moment.",

	// group listing
	'group_tools:groups:sorting:alphabetical' => "Alphabétique",
	'group_tools:groups:sorting:open' => "Ouvert",
	'group_tools:groups:sorting:closed' => "Fermé",
	'group_tools:groups:sorting:ordered' => "Trié",
	'group_tools:groups:sorting:suggested' => "Suggéré",

	// discussion status
	'group_tools:discussion:confirm:open' => "Êtes- vous sûr de vouloir rouvrir ce sujet de discussion?",
	'group_tools:discussion:confirm:close' => "Êtes-vous sûr de vouloir fermer ce sujet de discussion?",
	
	// allow group members to invite
	'group_tools:invite_members:title' => "Les membres du groupe peuvent inviter",
	'group_tools:invite_members:description' => "Autoriser les membres de ce groupe à inviter de nouveaux membres",

	// group tool option descriptions
	'activity:group_tool_option:description' => "Afficher un flux d'activité sur le contenu lié au groupe.",
	'forum:group_tool_option:description' => "Permettre aux membres du groupe pour entamer une discussion dans un format simple forum.",
	
	// actions
	'group_tools:action:error:input' => "Entrée non valide pour effectuer cette action",
	'group_tools:action:error:entities' => "Les GUID donnés ne résultaient pas dans les bonnes entités",
	'group_tools:action:error:entity' => "Les GUID données n'ont pas abouti à une entité correcte",
	'group_tools:action:error:edit' => "Vous n'avez pas accès à l' entité donnée",
	'group_tools:action:error:save' => "Il y a eu une erreur lors de l'enregistrement des paramètres",
	'group_tools:action:success' => "Les paramétrages ont été enregistrés avec succès",

	// admin transfer - action
	'group_tools:action:admin_transfer:error:access' => "Vous n'êtes pas autorisé à transférer la propriété de ce groupe",
	'group_tools:action:admin_transfer:error:self' => "Vous ne pouvez pas transférer la propriété à vous-même , vous êtes déjà propriétaire",
	'group_tools:action:admin_transfer:error:save' => "Une erreur inconnue est survenue lors de l'enregistrement du groupe, s'il vous plaît essayer à nouveau",
	'group_tools:action:admin_transfer:success' => "La propriété du Groupe a été transférée avec succès à %s",

	// group admins - action
	'group_tools:action:toggle_admin:error:group' => "L'entrée donné ne réside pas dans un groupe ou vous ne pouvez pas modifier ce groupe ou l'utilisateur n'est pas un membre",
	'group_tools:action:toggle_admin:error:remove' => "Une erreur inconnue est survenue lors de la suppression de l'utilisateur en tant qu'administrateur du groupe",
	'group_tools:action:toggle_admin:error:add' => "Une erreur inconnue est survenue lors de la suppression de l'utilisateur en tant qu'administrateur du groupe",
	'group_tools:action:toggle_admin:success:remove' => "L'utilisateur a été supprimé avec succès comme administrateur du groupe",
	'group_tools:action:toggle_admin:success:add' => "L'utilisateur a été ajouté avec succès comme administrateur du groupe",

	// group mail - action
	'group_tools:action:mail:success' => "Le message a été envoyé avec succès",

	// group - invite - action
	'group_tools:action:invite:error:invite'=> "Aucun utilisateurs invités (%s déjà invité, %s déjà membre)",
	'group_tools:action:invite:error:add'=> "Acun utilisateurs invités (%s déjà invité, %s déjà membre)",
	'group_tools:action:invite:success:invite'=> "%s utilisateurs invités (%s déjà invité et %s déjà membre)",
	'group_tools:action:invite:success:add'=> "%s utilisateurs rajoutés (%s déjà invité et %s déjà membre)",

	// group - invite - accept e-mail
	'group_tools:action:groups:email_invitation:error:input' => "SVP entrée un code d'invitation",
	'group_tools:action:groups:email_invitation:error:code' => "Le code d'invitation entré n'est plus valide",
	'group_tools:action:groups:email_invitation:error:join' => "Une erreur inconnue est survenue lors de l'adhésion au groupe %s, peut-être que vous êtes déjà membre",
	'group_tools:action:groups:email_invitation:success' => "Vous avez rejoint le groupe avec succès",

	// group - invite - decline e-mail
	'group_tools:action:groups:decline_email_invitation:error:delete' => "Une erreur est survenue lors de la suppression de l'invitation",

	// suggested groups
	'group_tools:suggested_groups:info' => "Les groupes suivants peuvent vous intéresser. Cliquez sur le bouton rejoindre le groupe pour le rejoindre immédiatement ou cliquez sur le titre pour voir plus d'informations sur le groupe.",
	'group_tools:suggested_groups:none' => "Nous ne pouvons pas proposer un groupe pour vous. Cela peut arriver si nous avons à peu d'informations sur vous, ou si vous êtes déjà un membre des groupes que nous aimerions que vous rejoignez. Utilisez la recherche pour trouver plus de groupes.",
		
	// group toggle auto join
	'group_tools:action:toggle_special_state:error:auto_join' => "Une erreur est survenue lors de l'enregistrement des paramètres d'adhésion automatique",
	'group_tools:action:toggle_special_state:error:suggested' => "Une erreur est survenue lors de l'enregistrement des nouveaux réglages suggérés",
	'group_tools:action:toggle_special_state:error:state' => "Etat non valide",
	'group_tools:action:toggle_special_state:auto_join' => "Les nou veaux paramètres d'adhésion automatique ont été correctement sauvegardés",
	'group_tools:action:toggle_special_state:suggested' => "Les nouveaux réglages suggérés ont été correctement sauvegardées",
	
	// group fix auto_join
	'group_tools:action:fix_auto_join:success' => "Appartenance au groupe fixée: %s nouveaux membres, %s étaient déjà membres et %s échoués",

	// group cleanup
	'group_tools:actions:cleanup:success' => "Les paramètres de nettoyage ont été correctement sauvegardés",

	// group default access
	'group_tools:actions:default_access:success' => "L'accès par défaut pour le groupe a été enregistré avec succès",

	// group notifications
	'group_tools:action:notifications:error:toggle' => "option toggle invalide ",
	'group_tools:action:notifications:success:disable' => "Les notifications ont été désactivées avec succès pour chaque membre",
	'group_tools:action:notifications:success:enable' => "Les notifications ont été activées avec succès pour chaque membre",

	// fix group problems
	'group_tools:action:fix_acl:error:input' => "Option non valide, vous ne pouvez pas corriger  %s",
	'group_tools:action:fix_acl:error:missing:nothing' => "Aucun utilisateurs manquants dans le groupe ACLs",
	'group_tools:action:fix_acl:error:excess:nothing' => "Aucun utilisateurs en excès trouvés dans les groupes ACLs",
	'group_tools:action:fix_acl:error:without:nothing' => "Pas de groupes trouvés sans ACL",

	'group_tools:action:fix_acl:success:missing' => " %d utilisateurs ajoutés avec succès aux groupes ACLs",
	'group_tools:action:fix_acl:success:excess' => " %d utilisateurs retirés des groupes ACLs",
	'group_tools:action:fix_acl:success:without' => "%d groupes ACLs créés",

	// discussion toggle status
	'group_tools:action:discussion:toggle_status:success:open' => "Le sujet a été rouvert avec succès",
	'group_tools:action:discussion:toggle_status:success:close' => "Le sujet a été fermé ",
		
	// Widgets
	// Group River Widget
	'widgets:group_river_widget:title' => "Activité du groupe",
    'widgets:group_river_widget:description' => "Affiche l'activité d'un groupe dans un widget",

    'widgets:group_river_widget:edit:num_display' => "Nombre d'activités",
	'widgets:group_river_widget:edit:group' => "Selectionner un groupe",
	'widgets:group_river_widget:edit:no_groups' => "Vous devez être membre d'au moins un groupe d'utiliser ce widget",

	'widgets:group_river_widget:view:not_configured' => "Ce widget n'est pas encore configuré",

	'widgets:group_river_widget:view:more' => "Activité dans le groupe '%s'",
	'widgets:group_river_widget:view:noactivity' => "Nous ne pouvions pas trouver d'activités.",

	// Group Members
	'widgets:group_members:title' => "Membres du groupe",
  	'widgets:group_members:description' => "Afficher les membres du group",

	'widgets:group_members:edit:num_display' => "Combien de membres afficher",
  	'widgets:group_members:view:no_members' => "Pas de membres de groupe trouvés",

  	// Group Invitations
	'widgets:group_invitations:title' => "Invitations au groupe",
  	'widgets:group_invitations:description' => "Affiche les invitations de groupe encours pour l'utilisateur actuel",

	// Discussion
	"widgets:discussion:settings:group_only" => "Afficher seulement les discussions de groupes dont vous êtes membre",
  	'widgets:discussion:more' => "Voir plus de discussions",
  	"widgets:discussion:description" => "Voir les dernières discussions",

	// Forum topic widget
	'widgets:group_forum_topics:description' => "Voir les dernières discussions",

	// index_groups
	'widgets:index_groups:description' => "Liste des groupes de votre communauté",
	'widgets:index_groups:show_members' => "Voir le nombre de membres",
	'widgets:index_groups:featured' => "Voir seulement les groupes sélectionnés / en vedette",
	'widgets:index_groups:sorting' => "Comment trier les groupes",

	'widgets:index_groups:filter:field' => "Filter les groupes par champs de groupe",
	'widgets:index_groups:filter:value' => "avec la valeur",
	'widgets:index_groups:filter:no_filter' => "Pas de filtres",

	// Featured Groups
	'widgets:featured_groups:description' => "Affiche une liste aléatoire de groupes vedette",
  	'widgets:featured_groups:edit:show_random_group' => "Afficher un groupe non vedette au hasard",

	// group_news widget
	"widgets:group_news:title" => "Actualités du groupe",
	"widgets:group_news:description" => "Affiche 5 derniers blogs de divers groupes",
	"widgets:group_news:no_projects" => "Pas de groupes configurés",
	"widgets:group_news:no_news" => "Pas de blogs pour ce groupe",
	"widgets:group_news:settings:project" => "Groupe",
	"widgets:group_news:settings:no_project" => "Selectionner un groupe",
	"widgets:group_news:settings:blog_count" => "Nombre maximum de blogs",
	"widgets:group_news:settings:group_icon_size" => "Taille de l'icône du groupe",
	"widgets:group_news:settings:group_icon_size:small" => "Petite",
	"widgets:group_news:settings:group_icon_size:medium" => "Moyenne",

	// quick start discussion
	'group_tools:widgets:start_discussion:title' => "Démarrer une discussion",
	'group_tools:widgets:start_discussion:description' => "Démarrer rapidement une discussion dans un groupe sélectionné",

	'group_tools:widgets:start_discussion:login_required' => "Pour utiliser ce widget vous devez être connecté",
	'group_tools:widgets:start_discussion:membership_required' => "Vous devez être membre d'au moins un groupe pour utiliser ce widget . Vous pouvez trouver des groupes intéressants %sici%s.",

	'group_tools:forms:discussion:quick_start:group' => "Sélectionner un groupe pour cette discussion",
	'group_tools:forms:discussion:quick_start:group:required' => "SVP sélectionnez un groupe",
	
	'groups:search:tags' => "recherche",
	'groups:search:title' => "Rechercher des groupes correspondants '%s'",
	'groups:searchtag' => "Rechercher des groupes",
	
	// welcome message
	'group_tools:welcome_message:title' => "Message d'accueil du groupe",
	'group_tools:welcome_message:description' => "Vous pouvez configurer un message de bienvenue pour les nouveaux utilisateurs qui se joignent à ce groupe. Si vous ne voulez pas envoyer un message de bienvenue laissez ce champ vide.",
	'group_tools:welcome_message:explain' => "Afin de personnaliser le message vous pouvez utiliser les variables suivantes:
[name]: le nom du nouvel utilisateur (eg. %s)
[group_name]: le nom de ce groupe (eg. %s)
[group_url]: l'URL de ce groupe (eg. %s)",
	
	'group_tools:action:welcome_message:success' => "Le message d'accueil a été enregistré",
	
	'group_tools:welcome_message:subject' => "Bienvenue à %s",
	
	// email invitations
	'group_tools:action:revoke_email_invitation:error' => "Une erreur est survenue lors de la révocation de l'invitation, s'il vous plaît essayer de nouveau",
	'group_tools:action:revoke_email_invitation:success' => "L'invitation a été révoquée",
	
	// domain based groups
	'group_tools:join:domain_based:tooltip' => "Votre domaine de messagerie ne vous autorise pas à rejoindre ce groupe.",
	
	'group_tools:domain_based:title' => "Configurer le domaine de messagerie",
	'group_tools:domain_based:description' => "Lorsque vous configurez un (ou plusieurs ) domaines de messagerie, les utilisateurs avec ces domaines e-mail pourront automatiquement rejoindre votre groupe lors de l'enregistrement. Même pour un groupe fermé, un utilisateur d'un domaine e-mail autorisé pourra rejoindre sans demander l'adhésion. Vous pouvez configurer plusieurs domaines en utilisant une virgule. Ne pas inclure le caractère @",
	
	'group_tools:action:domain_based:success' => "Le nouveau domaine de messagerie a été enregistré",
	
	// related groups
	'groups_tools:related_groups:tool_option' => "Voir les groupes liés",
	
	'groups_tools:related_groups:widget:title' => "Groupes liés",
	'groups_tools:related_groups:widget:description' => "Afficher une liste de groupes que vous avez ajoutés comme liés à ce groupe.",
	
	'groups_tools:related_groups:none' => "Pas de groupes liés trouvés.",
	'group_tools:related_groups:title' => "Groupes liés",
	
	'group_tools:related_groups:form:placeholder' => "Rechercher un nouveau groupe lié",
	'group_tools:related_groups:form:description' => "Vous pouvez rechercher un nouveau groupe lié, sélectionnez-le dans la liste et cliquez sur Ajouter.",
	
	'group_tools:action:related_groups:error:same' => "Vous ne pouvez pas lier ce groupe à lui-même",
	'group_tools:action:related_groups:error:already' => "Le groupe sélectionné est déjà lié",
	'group_tools:action:related_groups:error:add' => "Une erreur inconnue est survenue lors de l'ajout de la relation , s'il vous plaît essayer à nouveau",
	'group_tools:action:relatedgroups:success' => "Le groupe est maintenant lié",
	
	'group_tools:related_groups:notify:owner:subject' => "Un nouveau groupe lié a été ajouté",
	'group_tools:related_groups:notify:owner:message' => "Bonjour %s,
	
%s a ajouté votre groupe %s en tant que groupe relié au groupe %s.",
	
	'group_tools:related_groups:entity:remove' => "Supprimer la liaison de groupe",
	
	'group_tools:action:remove_related_groups:error:not_related' => "Ce groupe n'est pas lié",
	'group_tools:action:remove_related_groups:error:remove' => "Une erreur inconnue est survenue lors du retrait de la relation, s'il vous plaît essayer de nouveau",
	'group_tools:action:remove_related_groups:success' => "Ce groupe n'est plus lié",
	
	'group_tools:action:group_tool:presets:saved' => "Nouvel outil prédéfini pour un groupe enregistré",
	
	// group member export
	'group_tools:member_export:title_button' => "Exporter les membres",
	
	// group bulk delete
	'group_tools:action:bulk_delete:success' => "Les groupes sélectionnés ont été supprimés",
	'group_tools:action:bulk_delete:error' => "Une erreur est survenue lors de la suppression des groupes , s'il vous plaît essayer de nouveau",
);

add_translation("fr", $french);
