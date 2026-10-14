-- Optional timed sale: the catalog shows sale_price as the product's price between the two instants (UTC).
ALTER TABLE `products` ADD COLUMN `sale_price` double DEFAULT NULL;
ALTER TABLE `products` ADD COLUMN `sale_starts_at` datetime(6) DEFAULT NULL;
ALTER TABLE `products` ADD COLUMN `sale_ends_at` datetime(6) DEFAULT NULL;
