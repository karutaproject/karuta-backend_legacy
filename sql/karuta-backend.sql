-- Table creation only

--
-- Structure de la table `log`
--
CREATE TABLE IF NOT EXISTS `log_table` (
  `log_id` int(12) NOT NULL AUTO_INCREMENT,
  `log_date` datetime NOT NULL,
  `log_url` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `log_method` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
  `log_headers` text COLLATE utf8_unicode_ci NOT NULL,
  `log_in_body` text COLLATE utf8_unicode_ci,
  `log_out_body` text COLLATE utf8_unicode_ci,
  `log_code` int(12) NOT NULL,
  PRIMARY KEY (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Contenu de la table `log`
--


-- --------------------------------------------------------

--
-- Structure de la table `node`
--

CREATE TABLE IF NOT EXISTS `node` (
  `node_uuid` binary(16) NOT NULL,
  `node_parent_uuid` binary(16) DEFAULT NULL,
  `node_children_uuid` text COLLATE utf8_unicode_ci,
  `node_order` int(12) NOT NULL,
  `metadata` text COLLATE utf8_unicode_ci NOT NULL,
  `metadata_wad` text COLLATE utf8_unicode_ci NOT NULL,
  `metadata_epm` text COLLATE utf8_unicode_ci NOT NULL,
  `res_node_uuid` binary(16) DEFAULT NULL,
  `res_res_node_uuid` binary(16) DEFAULT NULL,
  `res_context_node_uuid` binary(16) DEFAULT NULL,
  `shared_res` int(1) NOT NULL,
  `shared_node` int(1) NOT NULL,
  `shared_node_res` int(1) NOT NULL,
  `shared_res_uuid` binary(16) DEFAULT NULL,
  `shared_node_uuid` binary(16) DEFAULT NULL,
  `shared_node_res_uuid` binary(16) DEFAULT NULL,
  `asm_type` varchar(50) COLLATE utf8_unicode_ci DEFAULT NULL,
  `xsi_type` varchar(50) COLLATE utf8_unicode_ci DEFAULT NULL,
  `semtag` varchar(250) COLLATE utf8_unicode_ci DEFAULT NULL,
  `semantictag` varchar(250) COLLATE utf8_unicode_ci DEFAULT NULL,
  `label` varchar(250) COLLATE utf8_unicode_ci DEFAULT NULL,
  `code` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
  `descr` varchar(250) COLLATE utf8_unicode_ci DEFAULT NULL,
  `format` varchar(30) COLLATE utf8_unicode_ci DEFAULT NULL,
  `modif_user_id` int(12) NOT NULL,
  `modif_date` timestamp NULL DEFAULT NULL,
  `portfolio_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`node_uuid`),
  KEY `portfolio_id` (`portfolio_id`),
  KEY `node_parent_uuid` (`node_parent_uuid`),
  KEY `node_order` (`node_order`),
  KEY `asm_type` (`asm_type`),
  KEY `res_node_uuid` (`res_node_uuid`),
  KEY `res_res_node_uuid` (`res_res_node_uuid`),
  KEY `res_context_node_uuid` (`res_context_node_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Contenu de la table `node`
--


-- --------------------------------------------------------

--
-- Structure de la table `portfolio`
--

CREATE TABLE IF NOT EXISTS `portfolio` (
  `portfolio_id` binary(16) NOT NULL,
  `root_node_uuid` binary(16) DEFAULT NULL,
  `user_id` int(12) NOT NULL,
  `model_id` binary(16) DEFAULT NULL,
  `modif_user_id` int(12) NOT NULL,
  `modif_date` timestamp NULL DEFAULT NULL,
  `active` int(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`portfolio_id`),
  KEY `root_node_uuid` (`root_node_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Contenu de la table `portfolio`
--


-- --------------------------------------------------------

--
-- Structure de la table `resource`
--

CREATE TABLE IF NOT EXISTS `resource_table` (
  `node_uuid` binary(16) NOT NULL,
  `xsi_type` varchar(50) COLLATE utf8_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8_unicode_ci,
  `user_id` int(11) DEFAULT NULL,
  `modif_user_id` int(12) NOT NULL,
  `modif_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`node_uuid`),
  FULLTEXT KEY `content` (`content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Contenu de la table `resource`
--

-- SECURITE



-- Credentials
CREATE TABLE IF NOT EXISTS `credential` (
  `userid` bigint(20) NOT NULL AUTO_INCREMENT,
  `login` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `can_substitute` int(11) NOT NULL DEFAULT '0',
  `is_admin` int(11) NOT NULL DEFAULT '0',
  `is_designer` int(11) NOT NULL DEFAULT '0',
  `is_sharer` int(11) NOT NULL DEFAULT '0',
  `active` int(11) NOT NULL DEFAULT '1',
  `display_firstname` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `display_lastname` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `email` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
  `password` binary(20) NOT NULL,
  `token` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
  `c_date` bigint(20) DEFAULT NULL,
  `other` varchar(255) DEFAULT '' NOT NULL,
  PRIMARY KEY (`userid`),
  UNIQUE KEY `login` (`login`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- credential_substitution
CREATE TABLE IF NOT EXISTS `credential_substitution` (
  `userid` bigint(20) NOT NULL,
  `id` bigint(20) NOT NULL,
  `type` enum('USER','GROUP') COLLATE utf8_unicode_ci NOT NULL DEFAULT 'USER',
  PRIMARY KEY (`userid`,`id`,`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- credential_group
CREATE TABLE IF NOT EXISTS `credential_group` (
  `cg` bigint(20) NOT NULL AUTO_INCREMENT,
  `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL UNIQUE,
  PRIMARY KEY (`cg`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- credential_group_members
CREATE TABLE IF NOT EXISTS `credential_group_members` (
  `cg` bigint(20) NOT NULL,
  `userid` bigint(20) NOT NULL,
  PRIMARY KEY (`cg`,`userid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Portfolio group
CREATE TABLE IF NOT EXISTS `portfolio_group` (
  `pg` bigint(20) NOT NULL AUTO_INCREMENT,
  `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL UNIQUE,
  `type` enum('GROUP','PORTFOLIO') COLLATE utf8_unicode_ci NOT NULL,
  `pg_parent` bigint(20),
  PRIMARY KEY (`pg`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
--

-- portfolio_group_members
CREATE TABLE IF NOT EXISTS `portfolio_group_members` (
  `pg` bigint(20) NOT NULL,
  `portfolio_id` binary(16) NOT NULL DEFAULT '\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0',
  PRIMARY KEY (`pg`,`portfolio_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- password = UNHEX(SHA1('password'))

-- Group Rights Info
-- change_rights when we give this group the possibility to change rights in other group at the same level
CREATE TABLE IF NOT EXISTS `group_right_info` (
  `grid` bigint(20) NOT NULL AUTO_INCREMENT,
  `owner` bigint(20) NOT NULL,
  `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT 'Nouveau groupe',
  `change_rights` tinyint(1) NOT NULL DEFAULT '0',
  `portfolio_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`grid`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Droit et groupe
CREATE TABLE IF NOT EXISTS `group_rights` (
  `grid` bigint(20) NOT NULL,
  `id` binary(16) NOT NULL,
  `RD` tinyint(1) NOT NULL DEFAULT '1',
  `WR` tinyint(1) NOT NULL DEFAULT '0',
  `DL` tinyint(1) NOT NULL DEFAULT '0',
  `SB` tinyint(1) NOT NULL DEFAULT '0',
  `AD` tinyint(1) NOT NULL DEFAULT '0',
  `types_id` text COLLATE utf8_unicode_ci,
  `rules_id` text COLLATE utf8_unicode_ci,
  `notify_roles` text COLLATE utf8_unicode_ci,
  PRIMARY KEY (`grid`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Group Info
CREATE TABLE IF NOT EXISTS `group_info` (
  `gid` bigint(20) NOT NULL AUTO_INCREMENT,
  `grid` bigint(20) DEFAULT NULL,
  `owner` bigint(20) NOT NULL,
  `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT 'Nouveau groupe',
  PRIMARY KEY (`gid`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Group with users
CREATE TABLE IF NOT EXISTS `group_user` (
  `gid` bigint(20) NOT NULL,
  `userid` bigint(20) NOT NULL,
  PRIMARY KEY (`gid`,`userid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Group with groups
CREATE TABLE IF NOT EXISTS `group_group` (
  `gid` bigint(20) NOT NULL,
  `child_gid` bigint(20) NOT NULL,
  PRIMARY KEY (`gid`,`child_gid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Annotation
CREATE TABLE IF NOT EXISTS `annotation` (
  `nodeid` binary(16) NOT NULL,
  `rank` int(11) NOT NULL,
  `text` text COLLATE utf8_unicode_ci,
  `c_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `a_user` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `wad_identifier` varchar(45) COLLATE utf8_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`nodeid`,`rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
--


-- Data
-- DEFAULT uuid2bin(UUID()) for id?, owner: uid from credential
CREATE TABLE IF NOT EXISTS `data_table` (
  `id` binary(16) NOT NULL,
  `owner` bigint(20) NOT NULL,
  `creator` bigint(20) NOT NULL,
  `type` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `mimetype` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
  `filename` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
  `c_date` bigint(20) DEFAULT NULL,
  `data` blob,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--


INSERT IGNORE INTO `credential` VALUES (1, 'root', 0, 1, 0, 1, 'root', '', NULL, UNHEX(SHA1('mati')), NULL, NULL, '');
INSERT IGNORE INTO `credential` VALUES (2, 'sys_public', 0, 0, 0, 1, 'System public account (users with account)', '', NULL, UNHEX(SHA1('THIS NEEDS TO BE CHANGED')), NULL, NULL, '');
INSERT IGNORE INTO `credential` VALUES (3, 'public', 0, 0, 0, 1, 'Public account (World)', '', NULL, UNHEX(SHA1('public')), NULL, NULL, '');

