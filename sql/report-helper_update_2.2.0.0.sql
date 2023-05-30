-- Table creation only

ALTER TABLE vector_table ADD COLUMN lineid bigint(20) NOT NULL;
ALTER TABLE vector_table MODIFY lineid bigint(20) NOT NULL AUTO_INCREMENT PRIMARY KEY;

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
