-- Updating tables from Karuta 2.2.0.0 to Karuta 2.2.0.1
-- Adding a display parameter for the client

USE `karuta-backend`;

ALTER TABLE `credential` ADD COLUMN `is_sharer` int(11) NOT NULL DEFAULT '0' AFTER is_designer;
