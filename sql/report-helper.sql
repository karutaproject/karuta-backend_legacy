-- Table creation only

--
-- Structure de la table `log`
--
CREATE TABLE IF NOT EXISTS `vector_table` (
  `lineid` bigint(20) NOT NULL AUTO_INCREMENT,
  
  `userid` bigint(20) NOT NULL,
  `date` datetime NOT NULL DEFAULT NOW(),
  `a1` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a2` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a3` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a4` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a5` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a6` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a7` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a8` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a9` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  `a10` varchar(255) COLLATE utf8_unicode_ci NOT NULL DEFAULT "",
  PRIMARY KEY(lineid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Contenu de la table `log`
--

CREATE TABLE IF NOT EXISTS `vector_usergroup` (
  `groupid` bigint(20) NOT NULL AUTO_INCREMENT,
  `userid` bigint(20) NOT NULL,
  `lineid` bigint(20) NOT NULL,
  PRIMARY KEY(groupid, userid, lineid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

CREATE TABLE IF NOT EXISTS `vector_rights` (
  `groupid` bigint(20) NOT NULL,
  
  `RD` tinyint(1) NOT NULL DEFAULT '1',
  `WR` tinyint(1) NOT NULL DEFAULT '0',
  `DL` tinyint(1) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;
