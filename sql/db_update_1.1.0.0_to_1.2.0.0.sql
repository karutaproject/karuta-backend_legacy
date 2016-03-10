-- Updating tables from Karuta 1.1.0.0 to Karuta 1.2.0.0

USE `karuta-backend`;

DROP TABLE `portfolio_group`;
CREATE TABLE IF NOT EXISTS `portfolio_group` (
  `pg` bigint(20) NOT NULL AUTO_INCREMENT,
  `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  `type` enum('GROUP','PORTFOLIO') COLLATE utf8_unicode_ci NOT NULL,
  `pg_parent` bigint(20),
  PRIMARY KEY (`pg`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

DROP TABLE `credential_group`;
CREATE TABLE IF NOT EXISTS `credential_group` (
  `cg` bigint(20) NOT NULL AUTO_INCREMENT,
  `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL UNIQUE,
  PRIMARY KEY (`cg`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

DROP TABLE `complete_share`;
-- Changes in the data cache which was introduced in 1.1.0.0
DROP TABLE `t_node_cache`;
