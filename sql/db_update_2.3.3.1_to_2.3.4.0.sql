-- Updating tables from Karuta 2.3.3.1 to Karuta 2.3.4.0
-- Adding a display parameter for the client

USE `karuta-backend`;

ALTER TABLE `credential` ADD COLUMN `date_limit` timestamp DEFAULT NULL AFTER c_date;
