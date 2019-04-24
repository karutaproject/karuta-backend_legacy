-- Helper function, to be used once when creating a new DB, or after
-- exporting/importing a DB

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


