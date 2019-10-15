-- Table creation only

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
  `res_content` text DEFAULT NULL,	-- (modif)
  `res_res_content` text DEFAULT NULL,	-- (modif)
  `res_context_content` text DEFAULT NULL,	-- (modif)
--  `shared_res` int(1) NOT NULL,	-- (modif)
--  `shared_node` int(1) NOT NULL,	-- (modif)
--  `shared_node_res` int(1) NOT NULL,	-- (modif)
--  `shared_res_uuid` binary(16) DEFAULT NULL,	-- (modif)
--  `shared_node_uuid` binary(16) DEFAULT NULL,	-- (modif)
--  `shared_node_res_uuid` binary(16) DEFAULT NULL,	-- (modif)
  `asm_type` varchar(50) COLLATE utf8_unicode_ci DEFAULT NULL,	-- Would be redundant with xsi_type_node
  `xsi_type_node` varchar(50) COLLATE utf8_unicode_ci DEFAULT NULL,	-- (changed) node type
  `xsi_type_res` varchar(50) COLLATE utf8_unicode_ci DEFAULT NULL,	-- (changed) content type
  `semtag` varchar(250) COLLATE utf8_unicode_ci DEFAULT NULL,
  `semantictag` varchar(250) COLLATE utf8_unicode_ci DEFAULT NULL,
  `label` varchar(250) COLLATE utf8_unicode_ci DEFAULT NULL,
  `code` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
  `descr` varchar(250) COLLATE utf8_unicode_ci DEFAULT NULL,
  `format` varchar(30) COLLATE utf8_unicode_ci DEFAULT NULL,
  `modif_user_id_node` int(12) NOT NULL,	-- (changed) container changed
  `modif_user_id_res` int(12) NOT NULL,	-- (changed) resource content changed
  `modif_date_node` timestamp NULL DEFAULT NULL,	-- (changed)
  `modif_date_res` timestamp NULL DEFAULT NULL,	-- (changed)
  `portfolio_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`node_uuid`),
  INDEX `node_parent_uuid` (`node_parent_uuid`),
  INDEX `portfolio_id` (`portfolio_id`)
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
  INDEX `root_node_uuid` (`root_node_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Contenu de la table `portfolio`
--


-- --------------------------------------------------------

-- SECURITE



-- Credentials
CREATE TABLE IF NOT EXISTS `credential` (
  `userid` bigint(20) NOT NULL AUTO_INCREMENT,
  `login` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `can_substitute` int(11) NOT NULL DEFAULT '0',
  `is_admin` int(11) NOT NULL DEFAULT '0',
  `is_designer` int(11) NOT NULL DEFAULT '0',
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
  PRIMARY KEY (`grid`),
  INDEX `portfolio_id` (`portfolio_id`)
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
  PRIMARY KEY (`gid`),
  INDEX `grid` (`grid`)
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



INSERT IGNORE INTO `credential` VALUES (1, 'root', 0, 1, 0, 1, 'root', '', NULL, UNHEX(SHA1('mati')), NULL, NULL, '');
INSERT IGNORE INTO `credential` VALUES (2, 'sys_public', 0, 0, 0, 1, 'System public account (users with account)', '', NULL, UNHEX(SHA1('THIS NEEDS TO BE CHANGED')), NULL, NULL, '');
INSERT IGNORE INTO `credential` VALUES (3, 'public', 0, 0, 0, 1, 'Public account (World)', '', NULL, UNHEX(SHA1('public')), NULL, NULL, '');

