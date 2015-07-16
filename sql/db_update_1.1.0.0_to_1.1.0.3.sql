-- Updating tables from Karuta 1.1.0.0 to Karuta 1.1.0.3

USE `karuta-backend`;

-- credential_group might have been used, better be safe and just alter it
ALTER TABLE credential_group MODIFY COLUMN `cg` bigint NOT NULL AUTO_INCREMENT;
ALTER TABLE credential_group ADD COLUMN `label` varchar(255) COLLATE utf8_unicode_ci NOT NULL;

DROP TABLE `complete_share`;
-- Changes in the data cache which was introduced in 1.1.0.0
DROP TABLE `t_node_cache`;
