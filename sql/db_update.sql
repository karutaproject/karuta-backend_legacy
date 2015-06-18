
USE `karuta-backend`;

SET autocommit = 0;

START TRANSACTION;

ALTER TABLE credential CHANGE uid userid BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE credential_substitution CHANGE uid userid BIGINT NOT NULL;
ALTER TABLE credential_group_members CHANGE uid userid BIGINT NOT NULL;
ALTER TABLE complete_share CHANGE uid userid BIGINT NOT NULL;
ALTER TABLE group_user CHANGE uid userid BIGINT NOT NULL;


ALTER TABLE credential CHANGE date c_date BIGINT;
ALTER TABLE annotation CHANGE date c_date timestamp DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE data CHANGE date c_date BIGINT;


ALTER TABLE annotation CHANGE user a_user VARCHAR(255) NOT NULL;


ALTER TABLE file CHANGE file filecontent longblob DEFAULT NULL;
ALTER TABLE file CHANGE size filesize int(11) DEFAULT NULL;


RENAME TABLE data TO data_table;
RENAME TABLE file TO file_table;
RENAME TABLE log TO log_table;
RENAME TABLE resource TO resource_table;
RENAME TABLE types TO types_table;


COMMIT;

//
DROP TABLE `complete_share`;
DROP TABLE `portfolio_group`;
CREATE TABLE IF NOT EXISTS `portfolio_group` (
  `owner` bigint(20) NOT NULL,
  `portfolio_id` binary(16) NOT NULL,
  `group_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
  PRIMARY KEY (`portfolio_id`,`group_name`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

// 16/06/2015
ALTER TABLE credential_group MODIFY COLUMN `cg` bigint NOT NULL AUTO_INCREMENT;
ALTER TABLE credential_group ADD COLUMN `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL;