-- Baseline of the ProductService schema as the JPA entities define it on 2026-10-08.
-- Every statement is IF NOT EXISTS, so it is a no-op on a database that Hibernate (ddl-auto=update)
-- already built, and builds the whole schema on a brand-new one. All services share one MySQL
-- schema, so each keeps its own history table (see spring.flyway.table).

CREATE TABLE IF NOT EXISTS `category` (
  `category_id` bigint NOT NULL AUTO_INCREMENT,
  `category_name` varchar(255) NOT NULL,
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `UKlroeo5fvfdeg4hpicn4lw7x9b` (`category_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `price_history` (
  `new_price` double NOT NULL,
  `old_price` double NOT NULL,
  `product_id` int DEFAULT NULL,
  `changed_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `product_image` (
  `product_id` int DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `image_url` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `products` (
  `gst_rate` double DEFAULT NULL,
  `low_stock_threshold` int NOT NULL,
  `product_price` double NOT NULL,
  `product_stock` int NOT NULL,
  `hsn_code` varchar(8) DEFAULT NULL,
  `product_id` bigint NOT NULL AUTO_INCREMENT,
  `variant_label` varchar(40) DEFAULT NULL,
  `variant_group` varchar(64) DEFAULT NULL,
  `product_category` varchar(255) DEFAULT NULL,
  `product_image_url` varchar(255) DEFAULT NULL,
  `product_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE IF NOT EXISTS `reviews` (
  `flagged` bit(1) NOT NULL,
  `hidden` bit(1) NOT NULL,
  `rating` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `review_id` bigint NOT NULL AUTO_INCREMENT,
  `reviewer_phno` bigint NOT NULL,
  `comment` varchar(255) DEFAULT NULL,
  `flag_reason` varchar(255) DEFAULT NULL,
  `reviewer_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`review_id`),
  UNIQUE KEY `UK2vgrgu2bt0isq5g8phlgu8tpt` (`product_id`,`reviewer_phno`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
