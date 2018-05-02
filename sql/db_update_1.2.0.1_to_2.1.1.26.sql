-- Updating tables from Karuta 1.2.0.0 to Karuta 2.1.1.26
-- Adding a display parameter for the client

USE `karuta-backend`;

ALTER TABLE `credential` ADD COLUMN `other` varchar(255) DEFAULT '' NOT NULL;