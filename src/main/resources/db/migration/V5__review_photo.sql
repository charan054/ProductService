-- Optional photo (an http/https image URL) a reviewer can attach to their review.
ALTER TABLE `reviews` ADD COLUMN `photo_url` varchar(500) DEFAULT NULL;
