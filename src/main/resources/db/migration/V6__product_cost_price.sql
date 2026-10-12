-- What one unit costs the shop (optional, never shown to customers); used to warn about discounts that sell below cost.
ALTER TABLE `products` ADD COLUMN `cost_price` double DEFAULT NULL;
