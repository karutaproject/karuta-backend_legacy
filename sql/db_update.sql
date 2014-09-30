
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
