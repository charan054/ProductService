-- When a product was first added, for the storefront "New" shelf. Existing products stay NULL (not new).
ALTER TABLE `products` ADD COLUMN `created_at` datetime(6) DEFAULT NULL;
