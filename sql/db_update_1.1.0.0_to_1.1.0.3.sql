-- Updating tables from Karuta 1.1.0.0 to Karuta 1.1.0.3

USE `karuta-backend`;

-- credential_group might have been used, better be safe and just alter it
ALTER TABLE credential_group MODIFY COLUMN `cg` bigint NOT NULL AUTO_INCREMENT;
ALTER TABLE credential_group ADD COLUMN `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL;

DROP TABLE `portfolio_group`;
CREATE TABLE IF NOT EXISTS `portfolio_group` (
  `pg` bigint(20) NOT NULL AUTO_INCREMENT,
  `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`pg`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

DROP TABLE `complete_share`;
-- Changes in the data cache which was introduced in 1.1.0.0
DROP TABLE `t_node_cache`;
