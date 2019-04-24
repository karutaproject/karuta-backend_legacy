-- SQL database and account creation, and grant to account


SET SQL_MODE="NO_AUTO_VALUE_ON_ZERO";

GRANT USAGE ON * . * TO 'karuta'@'localhost' IDENTIFIED BY 'karuta_password' WITH MAX_QUERIES_PER_HOUR 0 MAX_CONNECTIONS_PER_HOUR 0 MAX_UPDATES_PER_HOUR 0 MAX_USER_CONNECTIONS 0 ;

CREATE DATABASE IF NOT EXISTS `karuta-backend` ;

GRANT ALL PRIVILEGES ON `karuta-backend` . * TO 'karuta'@'localhost';


USE `karuta-backend` ;

