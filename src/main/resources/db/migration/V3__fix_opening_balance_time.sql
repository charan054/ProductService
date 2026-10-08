-- V2 stamped the opening-balance rows with NOW(6), which is the database server's local time, while every other
-- movement stores UTC. On a server that is not on UTC those rows therefore looked hours in the FUTURE and sorted above
-- real movements in "newest first". Move them back by the server's current offset from UTC (a no-op on a UTC server, and
-- on a database that has no such rows).
UPDATE `stock_movement`
SET `created_at` = DATE_SUB(`created_at`, INTERVAL TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(6), NOW(6)) MINUTE)
WHERE `actor` = 'system' AND `type` = 'INITIAL' AND `reason` LIKE 'Opening balance%';
