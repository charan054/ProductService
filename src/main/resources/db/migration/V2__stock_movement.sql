-- The stock ledger (StockMovement): one row per change to a product's stock.
CREATE TABLE IF NOT EXISTS `stock_movement` (
  `delta` int NOT NULL,
  `product_id` int DEFAULT NULL,
  `stock_after` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reference` varchar(100) DEFAULT NULL,
  `actor` varchar(64) NOT NULL,
  `reason` varchar(200) DEFAULT NULL,
  `type` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_stock_movement_product` (`product_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Opening balance: every product that already exists starts the ledger at its current stock, so the movements of
-- a product always add up to what is on the shelf.
INSERT INTO `stock_movement` (`delta`, `product_id`, `stock_after`, `created_at`, `reference`, `actor`, `reason`, `type`)
SELECT p.`product_stock`, p.`product_id`, p.`product_stock`, NOW(6), NULL, 'system',
       'Opening balance when the stock ledger was introduced', 'INITIAL'
FROM `products` p
WHERE NOT EXISTS (SELECT 1 FROM `stock_movement` m WHERE m.`product_id` = p.`product_id`);
