-- "Helpful" votes on reviews: one row per (review, voter), plus the running count kept on the review itself so
-- listings can sort by it.
ALTER TABLE `reviews` ADD COLUMN `helpful_count` int NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS `review_vote` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `review_id` bigint NOT NULL,
  `voter_phno` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_vote` (`review_id`,`voter_phno`),
  KEY `idx_review_vote_voter` (`voter_phno`)
);
