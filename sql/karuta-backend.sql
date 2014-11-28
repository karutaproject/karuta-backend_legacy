-- phpMyAdmin SQL Dump
-- version 3.2.0.1
-- http://www.phpmyadmin.net
--
-- Serveur: localhost
-- Généré le : Lun 29 Octobre 2012 à 11:24
-- Version du serveur: 5.1.36
-- Version de PHP: 5.3.0

SET SQL_MODE="NO_AUTO_VALUE_ON_ZERO";

--
-- Base de données: `my-backend`
--

GRANT USAGE ON * . * TO 'karuta'@'localhost' IDENTIFIED BY 'karuta_password' WITH MAX_QUERIES_PER_HOUR 0 MAX_CONNECTIONS_PER_HOUR 0 MAX_UPDATES_PER_HOUR 0 MAX_USER_CONNECTIONS 0 ;

CREATE DATABASE IF NOT EXISTS `karuta-backend` ;

GRANT ALL PRIVILEGES ON `karuta-backend` . * TO 'karuta'@'localhost';


USE `karuta-backend` ;

-- Server Script (source: http://bugs.mysql.com/bug.php?id=1214)
delimiter //

DROP FUNCTION IF EXISTS `uuid2bin`//
DROP FUNCTION IF EXISTS `bin2uuid`//

CREATE FUNCTION uuid2bin(uuid CHAR(36)) RETURNS BINARY(16) DETERMINISTIC
BEGIN
  RETURN UNHEX(REPLACE(uuid, '-',''));
END//

CREATE FUNCTION bin2uuid(bin BINARY(16)) RETURNS CHAR(36) DETERMINISTIC
BEGIN
  DECLARE hex CHAR(32);

  SET hex = HEX(bin);

  RETURN LOWER(CONCAT(LEFT(hex, 8),'-',
                      SUBSTR(hex, 9,4),'-',
                      SUBSTR(hex,13,4),'-',
                      SUBSTR(hex,17,4),'-',
                      RIGHT(hex, 12)
                          ));
END//

delimiter ;

-- --------------------------------------------------------

--
-- Structure de la table `file`
--

CREATE TABLE IF NOT EXISTS `file_table` (
  `node_uuid` binary(16) NOT NULL,
  `lang` varchar(10) COLLATE utf8_unicode_ci NOT NULL DEFAULT '',
  `name` varchar(254) COLLATE utf8_unicode_ci NOT NULL,
  `type` varchar(254) COLLATE utf8_unicode_ci DEFAULT NULL,
  `extension` varchar(10) COLLATE utf8_unicode_ci NOT NULL,
  `filesize` int(11) DEFAULT NULL,
  `filecontent` longblob,
  `modif_user_id` int(12) NOT NULL,
  `modif_date` datetime NOT NULL,
  PRIMARY KEY (`node_uuid`,`lang`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Contenu de la table `file`
--


-- --------------------------------------------------------

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
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

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
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

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
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

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
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

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
  `active` int(11) NOT NULL DEFAULT '1',
  `display_firstname` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `display_lastname` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `email` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
  `password` binary(20) NOT NULL,
  `token` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
  `c_date` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`userid`),
  UNIQUE KEY `login` (`login`)
) ENGINE=MyISAM AUTO_INCREMENT=2 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- credential_substitution
CREATE TABLE IF NOT EXISTS `credential_substitution` (
  `userid` bigint(20) NOT NULL,
  `id` bigint(20) NOT NULL,
  `type` enum('USER','GROUP') COLLATE utf8_unicode_ci NOT NULL DEFAULT 'USER',
  PRIMARY KEY (`userid`,`id`,`type`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- credential_group
CREATE TABLE IF NOT EXISTS `credential_group` (
  `cg` bigint(20) NOT NULL,
  PRIMARY KEY (`cg`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- credential_group_members
CREATE TABLE IF NOT EXISTS `credential_group_members` (
  `cg` bigint(20) NOT NULL,
  `userid` bigint(20) NOT NULL,
  PRIMARY KEY (`cg`,`userid`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- portfolio_group_members
CREATE TABLE IF NOT EXISTS `portfolio_group_members` (
  `pg` bigint(20) NOT NULL,
  `portfolio_id` binary(16) NOT NULL DEFAULT '\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0',
  PRIMARY KEY (`pg`,`portfolio_id`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- password = UNHEX(SHA1('password'))

-- Type definition when adding things
CREATE TABLE IF NOT EXISTS `definition_info` (
  `def_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT 'Nouveau Type',
  PRIMARY KEY (`def_id`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- node_id, parent_node: relative ID
CREATE TABLE IF NOT EXISTS `definition_type` (
  `node_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `def_id` bigint(20) NOT NULL,
  `asm_type` varchar(50) COLLATE utf8_unicode_ci DEFAULT NULL,
  `xsi_type` varchar(50) COLLATE utf8_unicode_ci DEFAULT NULL,
  `parent_node` bigint(20) DEFAULT NULL,
  `node_data` text COLLATE utf8_unicode_ci,
  `instance_rule` bigint(20) NOT NULL,
  PRIMARY KEY (`node_id`,`def_id`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Rules to be added
CREATE TABLE IF NOT EXISTS `rule_info` (
  `rule_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT 'Nouvelle Commande',
  PRIMARY KEY (`rule_id`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `rule_table` (
  `rule_id` bigint(20) NOT NULL,
  `role` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `RD` tinyint(1) NOT NULL DEFAULT '1',
  `WR` tinyint(1) NOT NULL DEFAULT '0',
  `DL` tinyint(1) NOT NULL DEFAULT '0',
  `AD` tinyint(1) NOT NULL DEFAULT '0',
  `types_id` text COLLATE utf8_unicode_ci,
  `rules_id` text COLLATE utf8_unicode_ci,
  PRIMARY KEY (`rule_id`,`role`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Group Rights Info
-- change_rights when we give this group the possibility to change rights in other group at the same level
CREATE TABLE IF NOT EXISTS `group_right_info` (
  `grid` bigint(20) NOT NULL AUTO_INCREMENT,
  `owner` bigint(20) NOT NULL,
  `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT 'Nouveau groupe',
  `change_rights` tinyint(1) NOT NULL DEFAULT '0',
  `portfolio_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`grid`)
) ENGINE=MyISAM AUTO_INCREMENT=3 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

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
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Group Info
CREATE TABLE IF NOT EXISTS `group_info` (
  `gid` bigint(20) NOT NULL AUTO_INCREMENT,
  `grid` bigint(20) DEFAULT NULL,
  `owner` bigint(20) NOT NULL,
  `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT 'Nouveau groupe',
  PRIMARY KEY (`gid`)
) ENGINE=MyISAM AUTO_INCREMENT=3 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `complete_share` (
  `userid` bigint(20) NOT NULL,
  `portfolio_id` binary(16) NOT NULL,
  PRIMARY KEY (`userid`,`portfolio_id`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Group with users
CREATE TABLE IF NOT EXISTS `group_user` (
  `gid` bigint(20) NOT NULL,
  `userid` bigint(20) NOT NULL,
  PRIMARY KEY (`gid`,`userid`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Group with groups
CREATE TABLE IF NOT EXISTS `group_group` (
  `gid` bigint(20) NOT NULL,
  `child_gid` bigint(20) NOT NULL,
  PRIMARY KEY (`gid`,`child_gid`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- Annotation
CREATE TABLE IF NOT EXISTS `annotation` (
  `nodeid` binary(16) NOT NULL,
  `rank` int(11) NOT NULL,
  `text` text COLLATE utf8_unicode_ci,
  `c_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `a_user` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `wad_identifier` varchar(45) COLLATE utf8_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`nodeid`,`rank`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
--

-- Portfolio group
CREATE TABLE IF NOT EXISTS `portfolio_group` (
  `owner` bigint(20) NOT NULL,
  `portfolio_id` binary(16) NOT NULL,
  `group_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`portfolio_id`,`group_name`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
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
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--


INSERT IGNORE INTO `credential` VALUES (1, 'root', 0, 1, 0, 1, 'root', '', NULL, UNHEX(SHA1('mati')), NULL, NULL);

INSERT INTO `group_info` VALUES (1,1,1,'all'),(2,2,1,'designer');

INSERT INTO `group_right_info` VALUES (1,1,'all',0,0x7402B09591254AD19E47035705A0A59E),(2,1,'designer',0,0x7402B09591254AD19E47035705A0A59E);
INSERT INTO `group_rights` VALUES (1,0x9486EC6B355E480285C3E9EDCBCB67AC,1,0,0,0,0,NULL,NULL,NULL),(1,0xCCE6C04B0DC94723BEB7C46D76BDDCDE,1,0,0,0,0,NULL,NULL,NULL),(1,0x578950760D504E5E94E79A295031660A,1,0,0,0,0,NULL,NULL,NULL),(1,0xF74AEB5A22A141AB91037CEDAFE8B513,1,0,0,0,0,NULL,NULL,NULL),(1,0x3EF063AC3B414100BB419EF2423C8908,1,0,0,0,0,NULL,NULL,NULL),(1,0x2E88C91BA15B4EF9BF1DE5F711238294,1,0,0,0,0,NULL,NULL,NULL),(1,0x18EA8A75867D4AEF8E4B856DE973BD00,1,0,0,0,0,NULL,NULL,NULL),(1,0x858B5B090DC544D39F2A8162A82F7A83,1,0,0,0,0,NULL,NULL,NULL),(1,0xD33138E6DB3D40C2AF95788F6E67FA1A,1,0,0,0,0,NULL,NULL,NULL),(1,0x0EC045DFF1924499BBEA4B73734A31E3,1,0,0,0,0,NULL,NULL,NULL),(1,0x171FF509BA534AE8988964EAC33545F3,1,0,0,0,0,NULL,NULL,NULL),(1,0x9930FA4BB7BF4580992C29A21E28BBE0,1,0,0,0,0,NULL,NULL,NULL),(1,0xEF9D1A303F5141108E20C217BD6C8011,1,0,0,0,0,NULL,NULL,NULL),(1,0x18C3C4A37FB84C41831AE0A285ECA6A5,1,0,0,0,0,NULL,NULL,NULL),(1,0x3B49144C79E442148FB9B327A37ACAB8,1,0,0,0,0,NULL,NULL,NULL),(1,0x7CCDD524873349869964B3C3DA8C36DB,1,0,0,0,0,NULL,NULL,NULL),(1,0x784053D800ED4A71807A6FB2921EF39D,1,0,0,0,0,NULL,NULL,NULL),(1,0x9005774C598A4E94A70660461F5D30EB,1,0,0,0,0,NULL,NULL,NULL),(1,0x5C685DB9031D477482D2F867457B7A12,1,0,0,0,0,NULL,NULL,NULL),(1,0xF697AB808535418796A2FA9EE98E2B7B,1,0,0,0,0,NULL,NULL,NULL),(1,0xC404DAFD6FD14D21A03F424E58EA7B3C,1,0,0,0,0,NULL,NULL,NULL),(1,0x2C26BEEC7D2445EF903DB69C0C8474AE,1,0,0,0,0,NULL,NULL,NULL),(1,0x0EAED1A3A1DE4F98AEB2E08C33F364C9,1,0,0,0,0,NULL,NULL,NULL),(1,0x6A77D35CDE8C4A8987583B22FB015757,1,0,0,0,0,NULL,NULL,NULL),(1,0xE3084EDA2C1E41759715D75EB5068D74,1,0,0,0,0,NULL,NULL,NULL),(1,0xCA042ED9F51B406E8BD3F3B0AF3C6B4F,1,0,0,0,0,NULL,NULL,NULL),(1,0x08EC8D5C8AAE4DCB8214BB0E5A31CC81,1,0,0,0,0,NULL,NULL,NULL),(1,0x399FE99B8B6E41D8AFE21FBC7B1B67A5,1,0,0,0,0,NULL,NULL,NULL),(1,0x1213C08A09D543BDBD41796C9AAD3A91,1,0,0,0,0,NULL,NULL,NULL),(1,0xA0B42FCADE3D4641A2B28221D0A5C879,1,0,0,0,0,NULL,NULL,NULL),(1,0x4F17CF9AB7FF486197193ACD98D4D6F5,1,0,0,0,0,NULL,NULL,NULL),(1,0x4700A49A503846A3B146476D80CAAA06,1,0,0,0,0,NULL,NULL,NULL),(1,0x732487C7B9CB4F93BD67716C906FAE89,1,0,0,0,0,NULL,NULL,NULL),(1,0x3A91B29E16F5468CA73B4BB5A45DAA5F,1,0,0,0,0,NULL,NULL,NULL);

INSERT INTO `group_user` VALUES (2,1);

INSERT INTO `node` VALUES (0x9486EC6B355E480285C3E9EDCBCB67AC,NULL,'cce6c04b-0dc9-4723-beb7-c46d76bddcde,57895076-0d50-4e5e-94e7-9a295031660a,f74aeb5a-22a1-41ab-9103-7cedafe8b513,3ef063ac-3b41-4100-bb41-9ef2423c8908,2e88c91b-a15b-4ef9-bf1d-e5f711238294,18ea8a75-867d-4aef-8e4b-856de973bd00,858b5b09-0dc5-44d3-9f2a-8162a82f7a83,d33138e6-db3d-40c2-af95-788f6e67fa1a,0ec045df-f192-4499-bbea-4b73734a31e3,171ff509',0,'sharedNode=\"N\" sharedNodeResource=\"N\" ','seenoderoles=\"all\" ','',NULL,0x16284455A1464481B2892632F9910159,0x3E39BE4F934B4174A64432C5A738C4DD,0,0,0,NULL,NULL,NULL,'asmRoot','asmRoot',NULL,NULL,NULL,'_karuta_resources_',NULL,NULL,1,'2014-09-29 17:08:46',0x7402B09591254AD19E47035705A0A59E),(0xCCE6C04B0DC94723BEB7C46D76BDDCDE,0x9486EC6B355E480285C3E9EDCBCB67AC,'',0,'multilingual-node=\"Y\" semantictag=\"asmStructure\" sharedNode=\"N\" sharedNodeResource=\"N\" ','seenoderoles=\"all\" ','',NULL,0x47295A0E1F824AD5AC8C72703C179239,0xD82F59D4E9BD4AB6AD4F08C6149FE40B,0,0,0,NULL,NULL,NULL,'asmStructure','asmStructure',NULL,'asmStructure',NULL,'',NULL,NULL,1,'2014-09-29 17:08:46',0x7402B09591254AD19E47035705A0A59E),(0x578950760D504E5E94E79A295031660A,0x9486EC6B355E480285C3E9EDCBCB67AC,'',1,'multilingual-node=\"Y\" semantictag=\"asmUnit\" sharedNode=\"N\" sharedNodeResource=\"N\" ','seenoderoles=\"all\" ','',NULL,0x62CCC9C9FB22407397F8C58AB4B994D5,0x8FB68DAA859F40B8A4ABA189E71EBC09,0,0,0,NULL,NULL,NULL,'asmUnit','asmUnit',NULL,'asmUnit',NULL,'',NULL,NULL,1,'2014-09-29 17:08:46',0x7402B09591254AD19E47035705A0A59E),(0xF74AEB5A22A141AB91037CEDAFE8B513,0x9486EC6B355E480285C3E9EDCBCB67AC,'',2,'multilingual-node=\"Y\" semantictag=\"asmUnitStructure\" sharedNode=\"N\" sharedNodeResource=\"N\" ','seenoderoles=\"all\" ','',NULL,0x35F1AC4DDBF94AC7B41F2C8B09D06F46,0x03AAD1DC0C114BB481D932CDE6B55F3C,0,0,0,NULL,NULL,NULL,'asmUnitStructure','asmUnitStructure',NULL,'asmUnitStructure',NULL,'',NULL,NULL,1,'2014-09-29 17:08:46',0x7402B09591254AD19E47035705A0A59E),(0x3EF063AC3B414100BB419EF2423C8908,0x9486EC6B355E480285C3E9EDCBCB67AC,'',3,'multilingual-node=\"Y\" semantictag=\"TextField\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0xC13DD032CE564F53B105A1A4745CC6FC,0x45F570FA1F6B433F8FE4335D6FDAB132,0xE891CC19FF704232A6A9121231633B2E,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'TextField',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x2E88C91BA15B4EF9BF1DE5F711238294,0x9486EC6B355E480285C3E9EDCBCB67AC,'',4,'multilingual-node=\"Y\" semantictag=\"Field\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0x0EA3E202163A4F3CAC3119CF640C733B,0x45DB658074464591A4A4049B24F47DE5,0x9582A5C54417403BB45675E22C6A5D2E,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Field',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x18EA8A75867D4AEF8E4B856DE973BD00,0x9486EC6B355E480285C3E9EDCBCB67AC,'',5,'multilingual-node=\"Y\" semantictag=\"Document\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0xBD5820C49A79486DA5E5BFB69A1C9CC0,0xF8A5469A8F714306978A98DBCD2FE81B,0x89699EF311FD4B54BD525E90E96D4E98,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Document',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x858B5B090DC544D39F2A8162A82F7A83,0x9486EC6B355E480285C3E9EDCBCB67AC,'',6,'multilingual-node=\"Y\" semantictag=\"Video\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0x0F1E11A075CD4A61853188FB500B7473,0x5E275DEA0B9144ED9B268FE66C2AD6BC,0x6965C7244EBF45B18A7BD551D2638BDA,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Video',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0xD33138E6DB3D40C2AF95788F6E67FA1A,0x9486EC6B355E480285C3E9EDCBCB67AC,'',7,'multilingual-node=\"Y\" semantictag=\"URL\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0xF54B593442FB4CC7BE5AA1B3B9321620,0x6055FC1F73F7495F90FBD1DA5D5856BA,0x5921CC683B614050B75D51949BAD4C1F,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'URL',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x0EC045DFF1924499BBEA4B73734A31E3,0x9486EC6B355E480285C3E9EDCBCB67AC,'',8,'multilingual-node=\"Y\" semantictag=\"Oembed\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0xB2906DB4E57A4B7AA047C41B33CD3607,0x8347E8E082C54F4799D31138D2392E4E,0xBA0DEEB93E6B48F189659CB103374157,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Oembed',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x171FF509BA534AE8988964EAC33545F3,0x9486EC6B355E480285C3E9EDCBCB67AC,'',9,'multilingual-node=\"Y\" semantictag=\"Calendar\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0x938D7DB9794A4E4199BBA527EECF6912,0x081F0C15439244EEBA6ADD2EEE5061B4,0xADEB6222414740AE9F8B67211A5B2590,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Calendar',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x9930FA4BB7BF4580992C29A21E28BBE0,0x9486EC6B355E480285C3E9EDCBCB67AC,'',10,'multilingual-node=\"Y\" semantictag=\"Image\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','width=\"100\" ',0xFF9837C476B44E988D6DA23954B10999,0xCFF314726B71474F92F0B390E3609E81,0x3EDB4352CBD74B2FBE441977CDABCB7A,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Image',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0xEF9D1A303F5141108E20C217BD6C8011,0x9486EC6B355E480285C3E9EDCBCB67AC,'',11,'multilingual-node=\"Y\" semantictag=\"Comments\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0x32E3CA1A078543F18E961B2D47A1FA11,0xDCC55696380C4DEEA30A3F22C3F2BAD1,0x378F482D84D640C48DC69F3E80E5832E,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Comments',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x18C3C4A37FB84C41831AE0A285ECA6A5,0x9486EC6B355E480285C3E9EDCBCB67AC,'',12,'multilingual-node=\"Y\" semantictag=\"Get_Resource\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0x12138262D2934AF681BD5287173D0D7B,0xC5E7BCC9799641489C0A4FFF7FC8D1EB,0xFE8115AE198846218F01BA083FADE43E,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Get_Resource',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x3B49144C79E442148FB9B327A37ACAB8,0x9486EC6B355E480285C3E9EDCBCB67AC,'',13,'multilingual-node=\"Y\" semantictag=\"Get_Get_Resource\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0x824AEC051CC9492EB256192149E28E58,0xC7D9D53589934BE0A130E47E3D34E03D,0xCC0B8CF1EE8442D9899D8740D1CCED5C,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Get_Get_Resource',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x7CCDD524873349869964B3C3DA8C36DB,0x9486EC6B355E480285C3E9EDCBCB67AC,'',14,'multilingual-node=\"Y\" semantictag=\"Proxy\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0x35587448B9AA49DC9CC595F615892E96,0xB6E515F35D164CB8B85918489C63B3BB,0x4C13FFCE16484C6CA2287ADB9674B20F,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Proxy',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x784053D800ED4A71807A6FB2921EF39D,0x9486EC6B355E480285C3E9EDCBCB67AC,'',15,'multilingual-node=\"Y\" semantictag=\"Get_Proxy\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0xC9B592D1BD264E68B8A7507DFB3DD135,0x670FC7FA653D434C82CABBDB0CA03376,0x846FA7A2F6C14BD2A740F86C18DF41EB,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Get_Proxy',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x9005774C598A4E94A70660461F5D30EB,0x9486EC6B355E480285C3E9EDCBCB67AC,'',16,'multilingual-node=\"Y\" semantictag=\"Item\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','',0xFFCDB052F50246CB944F7BBE6796AC19,0x93A4F6CCD6294502B947F214E5A2B94D,0x1F66AC149B494F80B09E6032CA8B1C32,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Item',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x5C685DB9031D477482D2F867457B7A12,0x9486EC6B355E480285C3E9EDCBCB67AC,'f697ab80-8535-4187-96a2-fa9ee98e2b7b',17,'multilingual-node=\"Y\" semantictag=\"\" sharedNode=\"N\" sharedNodeResource=\"N\" ','contentfreenode=\"Y\" seenoderoles=\"all\" ','',NULL,0x047113ED418949D3852F23B37F1DCA2F,0xE253A810C48340C6AB6CE464DB5DD3B8,0,0,0,NULL,NULL,NULL,'asmUnit','asmUnit',NULL,'',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0xF697AB808535418796A2FA9EE98E2B7B,0x5C685DB9031D477482D2F867457B7A12,'',0,'multilingual-node=\"Y\" semantictag=\"asmUnitStructure_free\" sharedNode=\"N\" sharedNodeResource=\"N\" ','seenoderoles=\"all\" ','height=\"50px\" top=\"30\" width=\"600px\" ',NULL,0x4CBF9B6B032D44B0BD6D25CAAE11E55E,0xAC4795F1F84E49E28E81ABA589A6DACE,0,0,0,NULL,NULL,NULL,'asmUnitStructure','asmUnitStructure',NULL,'asmUnitStructure_free',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0xC404DAFD6FD14D21A03F424E58EA7B3C,0x9486EC6B355E480285C3E9EDCBCB67AC,'2c26beec-7d24-45ef-903d-b69c0c8474ae',18,'multilingual-node=\"Y\" semantictag=\"\" sharedNode=\"N\" sharedNodeResource=\"N\" ','contentfreenode=\"Y\" seenoderoles=\"all\" ','',NULL,0x6171DF7DCD6A484481D5EC39A6E7A282,0x6F9A922EA0D34832A6DCAED176E096CE,0,0,0,NULL,NULL,NULL,'asmUnit','asmUnit',NULL,'',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x2C26BEEC7D2445EF903DB69C0C8474AE,0xC404DAFD6FD14D21A03F424E58EA7B3C,'',0,'multilingual-node=\"Y\" semantictag=\"TextField_free\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','height=\"50px\" left=\"300\" top=\"30\" width=\"400px\" ',0x9512E97710AD4DBAA19CEEF438EC25B4,0x212B9394E4E64157BA4CCBE60EECE3FC,0xE4C93B63226C437F952AD7F049897DA7,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'TextField_free',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x0EAED1A3A1DE4F98AEB2E08C33F364C9,0x9486EC6B355E480285C3E9EDCBCB67AC,'6a77d35c-de8c-4a89-8758-3b22fb015757',19,'multilingual-node=\"Y\" semantictag=\"\" sharedNode=\"N\" sharedNodeResource=\"N\" ','contentfreenode=\"Y\" seenoderoles=\"all\" ','',NULL,0xC841745F6943403793A1D9FD5EB840DF,0x1C73F95CDF084BDEB9C4CBCCF9BBF339,0,0,0,NULL,NULL,NULL,'asmUnit','asmUnit',NULL,'',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x6A77D35CDE8C4A8987583B22FB015757,0x0EAED1A3A1DE4F98AEB2E08C33F364C9,'',0,'multilingual-node=\"Y\" semantictag=\"Field_free\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','height=\"35px\" left=\"300\" top=\"30\" width=\"400px\" ',0xF9C0D890B67A4E04BD2F7573197EDED8,0x35F4B26A04A147F8A7E6DAF3F5D9B6E8,0xFE4C3EFA194F454284C4C6F968B26E75,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Field_free',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0xE3084EDA2C1E41759715D75EB5068D74,0x9486EC6B355E480285C3E9EDCBCB67AC,'ca042ed9-f51b-406e-8bd3-f3b0af3c6b4f',20,'multilingual-node=\"Y\" semantictag=\"\" sharedNode=\"N\" sharedNodeResource=\"N\" ','contentfreenode=\"Y\" seenoderoles=\"all\" ','',NULL,0x48A32F0CC79C4BF492FC4835495CDB19,0x46DBF555438B460F9FB73F54A61F990D,0,0,0,NULL,NULL,NULL,'asmUnit','asmUnit',NULL,'',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0xCA042ED9F51B406E8BD3F3B0AF3C6B4F,0xE3084EDA2C1E41759715D75EB5068D74,'',0,'multilingual-node=\"Y\" semantictag=\"Document_free\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','height=\"35px\" left=\"300px\" top=\"30px\" width=\"400px\" ',0x318BD673EA1B4C5691A9151BC4627F33,0x3A2E86B570EA496CAE7042B7F1500257,0xA1401764F6754BEEB1A34DDF51F99F96,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Document_free',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x08EC8D5C8AAE4DCB8214BB0E5A31CC81,0x9486EC6B355E480285C3E9EDCBCB67AC,'399fe99b-8b6e-41d8-afe2-1fbc7b1b67a5',21,'multilingual-node=\"Y\" semantictag=\"\" sharedNode=\"N\" sharedNodeResource=\"N\" ','contentfreenode=\"Y\" seenoderoles=\"all\" ','',NULL,0xC787599B40644617A70246571F691DA0,0xC66CDBE20AA84017A6D7F2A80C3D1AE9,0,0,0,NULL,NULL,NULL,'asmUnit','asmUnit',NULL,'',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x399FE99B8B6E41D8AFE21FBC7B1B67A5,0x08EC8D5C8AAE4DCB8214BB0E5A31CC81,'',0,'multilingual-node=\"Y\" semantictag=\"URL_free\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','height=\"35px\" left=\"300px\" top=\"30px\" width=\"400px\" ',0x75367F16389A47328BACB99F82FDDC45,0x6E49C66D2B1840148F2ECA3A271D8181,0x8EC35093160B4AEC98320B07F1CAB05B,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'URL_free',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0x1213C08A09D543BDBD41796C9AAD3A91,0x9486EC6B355E480285C3E9EDCBCB67AC,'a0b42fca-de3d-4641-a2b2-8221d0a5c879',22,'multilingual-node=\"Y\" semantictag=\"\" sharedNode=\"N\" sharedNodeResource=\"N\" ','contentfreenode=\"Y\" seenoderoles=\"all\" ','',NULL,0xA16C5E81311D435EB2D5A66085C474B4,0x9CD47E2300B64146A719A3FB4E34DEF1,0,0,0,NULL,NULL,NULL,'asmUnit','asmUnit',NULL,'',NULL,'',NULL,NULL,1,'2014-09-29 17:08:47',0x7402B09591254AD19E47035705A0A59E),(0xA0B42FCADE3D4641A2B28221D0A5C879,0x1213C08A09D543BDBD41796C9AAD3A91,'',0,'multilingual-node=\"Y\" semantictag=\"Calendar_free\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','height=\"35px\" left=\"300px\" top=\"30px\" width=\"400px\" ',0xC0BD49B3DE7647569E7B24D71F6E5E74,0x5C765F636CF448B4B0EDC4C2DCFAA6E2,0xA073B7DB983F47B1A9D7A4098A6F8A19,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Calendar_free',NULL,'',NULL,NULL,1,'2014-09-29 17:08:48',0x7402B09591254AD19E47035705A0A59E),(0x4F17CF9AB7FF486197193ACD98D4D6F5,0x9486EC6B355E480285C3E9EDCBCB67AC,'4700a49a-5038-46a3-b146-476d80caaa06',23,'multilingual-node=\"Y\" semantictag=\"\" sharedNode=\"N\" sharedNodeResource=\"N\" ','contentfreenode=\"Y\" seenoderoles=\"all\" ','',NULL,0x70256B5B3EE84306A7B111B9BC9B031A,0x419215EF59654AB69466BA8EC1643518,0,0,0,NULL,NULL,NULL,'asmUnit','asmUnit',NULL,'',NULL,'',NULL,NULL,1,'2014-09-29 17:08:48',0x7402B09591254AD19E47035705A0A59E),(0x4700A49A503846A3B146476D80CAAA06,0x4F17CF9AB7FF486197193ACD98D4D6F5,'',0,'multilingual-node=\"Y\" semantictag=\"Image_free\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','height=\"100px\" left=\"300px\" top=\"30px\" width=\"400px\" ',0xC27BED49519E4B22BFD057C51352DF92,0x37D5E765EF484B4C96C5085F8109BC3A,0xC88A098839B34023925D1E7ECCCB3152,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Image_free',NULL,'',NULL,NULL,1,'2014-09-29 17:08:48',0x7402B09591254AD19E47035705A0A59E),(0x732487C7B9CB4F93BD67716C906FAE89,0x9486EC6B355E480285C3E9EDCBCB67AC,'3a91b29e-16f5-468c-a73b-4bb5a45daa5f',24,'multilingual-node=\"Y\" semantictag=\"\" sharedNode=\"N\" sharedNodeResource=\"N\" ','contentfreenode=\"Y\" seenoderoles=\"all\" ','',NULL,0x5FB0DE4A690D4FBF835536B9F6EE5898,0xBC181EB5BEAF4E40ACDFBD87BBAE83CF,0,0,0,NULL,NULL,NULL,'asmUnit','asmUnit',NULL,'',NULL,'',NULL,NULL,1,'2014-09-29 17:08:48',0x7402B09591254AD19E47035705A0A59E),(0x3A91B29E16F5468CA73B4BB5A45DAA5F,0x732487C7B9CB4F93BD67716C906FAE89,'',0,'multilingual-node=\"Y\" semantictag=\"Video_free\" sharedNode=\"N\" sharedNodeResource=\"N\" sharedResource=\"N\" ','seenoderoles=\"all\" ','height=\"400px\" left=\"300\" top=\"30px\" width=\"600px\" ',0x1117E89693414968957F50F05F18C1DF,0x62EB471EFE474A21B8CBFF7B03A85A68,0x043E2E46ECD04A9A9F4D50637EFDDC2F,0,0,0,NULL,NULL,NULL,'asmContext','',NULL,'Video_free',NULL,'',NULL,NULL,1,'2014-09-29 17:08:48',0x7402B09591254AD19E47035705A0A59E);
INSERT INTO `portfolio` VALUES (0x7402B09591254AD19E47035705A0A59E,0x9486EC6B355E480285C3E9EDCBCB67AC,1,NULL,1,'2014-09-29 17:08:48',1);
INSERT INTO `resource_table` VALUES (0x16284455A1464481B2892632F9910159,'nodeRes','<code>_karuta_resources_</code>\n			\n<label lang=\"fr\">_karuta_resources_</label>\n			\n<label lang=\"en\">_karuta_resources_</label>',1,1,'2014-09-29 17:08:46'),(0x3E39BE4F934B4174A64432C5A738C4DD,'context','',1,1,'2014-09-29 17:08:46'),(0x47295A0E1F824AD5AC8C72703C179239,'nodeRes','<code/>\n<label lang=\"fr\">Nouvelle structure</label>\n<label lang=\"en\">New Structure</label>',1,1,'2014-09-29 17:08:46'),(0xD82F59D4E9BD4AB6AD4F08C6149FE40B,'context','',1,1,'2014-09-29 17:08:46'),(0x62CCC9C9FB22407397F8C58AB4B994D5,'nodeRes','<code/>\n<label lang=\"fr\">Nouvelle Unité</label>\n<label lang=\"en\">New Unit</label>',1,1,'2014-09-29 17:08:46'),(0x8FB68DAA859F40B8A4ABA189E71EBC09,'context','',1,1,'2014-09-29 17:08:46'),(0x35F1AC4DDBF94AC7B41F2C8B09D06F46,'nodeRes','<code/>\n<label lang=\"fr\">Nouvelle section</label>\n<label lang=\"en\">New Section</label>',1,1,'2014-09-29 17:08:46'),(0x03AAD1DC0C114BB481D932CDE6B55F3C,'context','',1,1,'2014-09-29 17:08:46'),(0x45F570FA1F6B433F8FE4335D6FDAB132,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xE891CC19FF704232A6A9121231633B2E,'context','',1,1,'2014-09-29 17:08:47'),(0xC13DD032CE564F53B105A1A4745CC6FC,'TextField','<text lang=\"fr\"/>\n<text lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x45DB658074464591A4A4049B24F47DE5,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x9582A5C54417403BB45675E22C6A5D2E,'context','',1,1,'2014-09-29 17:08:47'),(0x0EA3E202163A4F3CAC3119CF640C733B,'Field','<text lang=\"fr\"/>\n<text lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xF8A5469A8F714306978A98DBCD2FE81B,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x89699EF311FD4B54BD525E90E96D4E98,'context','',1,1,'2014-09-29 17:08:47'),(0xBD5820C49A79486DA5E5BFB69A1C9CC0,'Document','<filename lang=\"fr\"/>\n<size lang=\"fr\"/>\n<type lang=\"fr\"/>\n<fileid lang=\"fr\"/>\n<filename lang=\"en\"/>\n<size lang=\"en\"/>\n<type lang=\"en\"/>\n<fileid lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x5E275DEA0B9144ED9B268FE66C2AD6BC,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x6965C7244EBF45B18A7BD551D2638BDA,'context','',1,1,'2014-09-29 17:08:47'),(0x0F1E11A075CD4A61853188FB500B7473,'Video','<filename lang=\"fr\"/>\n<size lang=\"fr\"/>\n<type lang=\"fr\"/>\n<fileid lang=\"fr\"/>\n<filename lang=\"en\"/>\n<size lang=\"en\"/>\n<type lang=\"en\"/>\n<fileid lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x6055FC1F73F7495F90FBD1DA5D5856BA,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x5921CC683B614050B75D51949BAD4C1F,'context','',1,1,'2014-09-29 17:08:47'),(0xF54B593442FB4CC7BE5AA1B3B9321620,'URL','<label lang=\"fr\"/>\n<label lang=\"en\"/>\n<url lang=\"fr\"/>\n<url lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x8347E8E082C54F4799D31138D2392E4E,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xBA0DEEB93E6B48F189659CB103374157,'context','',1,1,'2014-09-29 17:08:47'),(0xB2906DB4E57A4B7AA047C41B33CD3607,'Oembed','<url lang=\"fr\"/>\n<url lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x081F0C15439244EEBA6ADD2EEE5061B4,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xADEB6222414740AE9F8B67211A5B2590,'context','',1,1,'2014-09-29 17:08:47'),(0x938D7DB9794A4E4199BBA527EECF6912,'Calendar','<text lang=\"fr\"/>',1,1,'2014-09-29 17:08:47'),(0xCFF314726B71474F92F0B390E3609E81,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x3EDB4352CBD74B2FBE441977CDABCB7A,'context','',1,1,'2014-09-29 17:08:47'),(0xFF9837C476B44E988D6DA23954B10999,'Image','<filename lang=\"fr\"/>\n<size lang=\"fr\"/>\n<type lang=\"fr\"/>\n<fileid lang=\"fr\"/>\n<filename lang=\"en\"/>\n<size lang=\"en\"/>\n<type lang=\"en\"/>\n<fileid lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xDCC55696380C4DEEA30A3F22C3F2BAD1,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x378F482D84D640C48DC69F3E80E5832E,'context','',1,1,'2014-09-29 17:08:47'),(0x32E3CA1A078543F18E961B2D47A1FA11,'Comments','<author/>\n<date/>\n<text lang=\"fr\"/>\n<text lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xC5E7BCC9799641489C0A4FFF7FC8D1EB,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xFE8115AE198846218F01BA083FADE43E,'context','',1,1,'2014-09-29 17:08:47'),(0x12138262D2934AF681BD5287173D0D7B,'Get_Resource','<code/>\n<value/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xC7D9D53589934BE0A130E47E3D34E03D,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xCC0B8CF1EE8442D9899D8740D1CCED5C,'context','',1,1,'2014-09-29 17:08:47'),(0x824AEC051CC9492EB256192149E28E58,'Get_Get_Resource','<code/>\n<portfoliocode/>\n<value/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xB6E515F35D164CB8B85918489C63B3BB,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x4C13FFCE16484C6CA2287ADB9674B20F,'context','',1,1,'2014-09-29 17:08:47'),(0x35587448B9AA49DC9CC595F615892E96,'Proxy','<code/>\n<value/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x670FC7FA653D434C82CABBDB0CA03376,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x846FA7A2F6C14BD2A740F86C18DF41EB,'context','',1,1,'2014-09-29 17:08:47'),(0xC9B592D1BD264E68B8A7507DFB3DD135,'Get_Proxy','<code/>\n<value/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x93A4F6CCD6294502B947F214E5A2B94D,'nodeRes','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x1F66AC149B494F80B09E6032CA8B1C32,'context','',1,1,'2014-09-29 17:08:47'),(0xFFCDB052F50246CB944F7BBE6796AC19,'Item','<code/>\n<label lang=\"fr\"/>\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x047113ED418949D3852F23B37F1DCA2F,'nodeRes','<code/>\n<label lang=\"fr\">asmUnitStructure_free</label>\n<label lang=\"en\">asmUnitStructure_free</label>',1,1,'2014-09-29 17:08:47'),(0xE253A810C48340C6AB6CE464DB5DD3B8,'context','',1,1,'2014-09-29 17:08:47'),(0x4CBF9B6B032D44B0BD6D25CAAE11E55E,'nodeRes','<code/>\n					\n<label lang=\"fr\">Nouvelle section</label>\n					\n<label lang=\"en\">New Section</label>',1,1,'2014-09-29 17:08:47'),(0xAC4795F1F84E49E28E81ABA589A6DACE,'context','',1,1,'2014-09-29 17:08:47'),(0x6171DF7DCD6A484481D5EC39A6E7A282,'nodeRes','<code/>\n<label lang=\"fr\">TextField_free</label>\n<label lang=\"en\">TextField_free</label>',1,1,'2014-09-29 17:08:47'),(0x6F9A922EA0D34832A6DCAED176E096CE,'context','',1,1,'2014-09-29 17:08:47'),(0x212B9394E4E64157BA4CCBE60EECE3FC,'nodeRes','<code/>\n					\n<label lang=\"fr\"/>\n					\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xE4C93B63226C437F952AD7F049897DA7,'context','',1,1,'2014-09-29 17:08:47'),(0x9512E97710AD4DBAA19CEEF438EC25B4,'TextField','<text lang=\"fr\"/>\n					\n<text lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xC841745F6943403793A1D9FD5EB840DF,'nodeRes','<code/>\n<label lang=\"fr\">Field_free</label>\n<label lang=\"en\">Field_free</label>',1,1,'2014-09-29 17:08:47'),(0x1C73F95CDF084BDEB9C4CBCCF9BBF339,'context','',1,1,'2014-09-29 17:08:47'),(0x35F4B26A04A147F8A7E6DAF3F5D9B6E8,'nodeRes','<code/>\n					\n<label lang=\"fr\"/>\n					\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xFE4C3EFA194F454284C4C6F968B26E75,'context','',1,1,'2014-09-29 17:08:47'),(0xF9C0D890B67A4E04BD2F7573197EDED8,'Field','<text/>',1,1,'2014-09-29 17:08:47'),(0x48A32F0CC79C4BF492FC4835495CDB19,'nodeRes','<code/>\n<label lang=\"fr\">Document_free</label>\n<label lang=\"en\">Document_free</label>',1,1,'2014-09-29 17:08:47'),(0x46DBF555438B460F9FB73F54A61F990D,'context','',1,1,'2014-09-29 17:08:47'),(0x3A2E86B570EA496CAE7042B7F1500257,'nodeRes','<code/>\n					\n<label lang=\"fr\"/>\n					\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xA1401764F6754BEEB1A34DDF51F99F96,'context','',1,1,'2014-09-29 17:08:47'),(0x318BD673EA1B4C5691A9151BC4627F33,'Document','<filename lang=\"fr\"/>\n					\n<size lang=\"fr\"/>\n					\n<type lang=\"fr\"/>\n					\n<fileid lang=\"fr\"/>\n					\n<filename lang=\"en\"/>\n					\n<size lang=\"en\"/>\n					\n<type lang=\"en\"/>\n					\n<fileid lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0xC787599B40644617A70246571F691DA0,'nodeRes','<code/>\n<label lang=\"fr\">URL_free</label>\n<label lang=\"en\">URL_free</label>',1,1,'2014-09-29 17:08:47'),(0xC66CDBE20AA84017A6D7F2A80C3D1AE9,'context','',1,1,'2014-09-29 17:08:47'),(0x6E49C66D2B1840148F2ECA3A271D8181,'nodeRes','<code/>\n					\n<label lang=\"fr\"/>\n					\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:47'),(0x8EC35093160B4AEC98320B07F1CAB05B,'context','',1,1,'2014-09-29 17:08:47'),(0x75367F16389A47328BACB99F82FDDC45,'URL','<label>url</label>\n					\n<url/>',1,1,'2014-09-29 17:08:47'),(0xA16C5E81311D435EB2D5A66085C474B4,'nodeRes','<code/>\n<label lang=\"fr\">Calendar_free</label>\n<label lang=\"en\">Calendar_free</label>',1,1,'2014-09-29 17:08:47'),(0x9CD47E2300B64146A719A3FB4E34DEF1,'context','',1,1,'2014-09-29 17:08:47'),(0x5C765F636CF448B4B0EDC4C2DCFAA6E2,'nodeRes','<code/>\n					\n<label lang=\"fr\"/>\n					\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:48'),(0xA073B7DB983F47B1A9D7A4098A6F8A19,'context','',1,1,'2014-09-29 17:08:48'),(0xC0BD49B3DE7647569E7B24D71F6E5E74,'Calendar','<text/>',1,1,'2014-09-29 17:08:48'),(0x70256B5B3EE84306A7B111B9BC9B031A,'nodeRes','<code/>\n<label lang=\"fr\">Image_free</label>\n<label lang=\"en\">Image_free</label>',1,1,'2014-09-29 17:08:48'),(0x419215EF59654AB69466BA8EC1643518,'context','',1,1,'2014-09-29 17:08:48'),(0x37D5E765EF484B4C96C5085F8109BC3A,'nodeRes','<code/>\n					\n<label lang=\"fr\"/>\n					\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:48'),(0xC88A098839B34023925D1E7ECCCB3152,'context','',1,1,'2014-09-29 17:08:48'),(0xC27BED49519E4B22BFD057C51352DF92,'Image','<filename lang=\"fr\"/>\n					\n<size lang=\"fr\"/>\n					\n<type lang=\"fr\"/>\n					\n<fileid lang=\"fr\"/>\n					\n<filename lang=\"en\"/>\n					\n<size lang=\"en\"/>\n					\n<type lang=\"en\"/>\n					\n<fileid lang=\"en\"/>',1,1,'2014-09-29 17:08:48'),(0x5FB0DE4A690D4FBF835536B9F6EE5898,'nodeRes','<code/>\n<label lang=\"fr\">Video_free</label>\n<label lang=\"en\">Video_free</label>',1,1,'2014-09-29 17:08:48'),(0xBC181EB5BEAF4E40ACDFBD87BBAE83CF,'context','',1,1,'2014-09-29 17:08:48'),(0x62EB471EFE474A21B8CBFF7B03A85A68,'nodeRes','<code/>\n					\n<label lang=\"fr\"/>\n					\n<label lang=\"en\"/>',1,1,'2014-09-29 17:08:48'),(0x043E2E46ECD04A9A9F4D50637EFDDC2F,'context','',1,1,'2014-09-29 17:08:48'),(0x1117E89693414968957F50F05F18C1DF,'Video','<filename lang=\"fr\"/>\n					\n<size lang=\"fr\"/>\n					\n<type lang=\"fr\"/>\n					\n<fileid lang=\"fr\"/>\n					\n<filename lang=\"en\"/>\n					\n<size lang=\"en\"/>\n					\n<type lang=\"en\"/>\n					\n<fileid lang=\"en\"/>',1,1,'2014-09-29 17:08:48');

