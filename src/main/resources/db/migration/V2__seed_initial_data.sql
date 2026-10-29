-- V2__seed_initial_data.sql
-- Seed users and catalog data

INSERT INTO users (username, email, password, role, created_at, updated_at)
VALUES
    ('admin', 'admin@orderflow.local', '$2b$12$90SlVFxRJeqxfqpz6.TFs.12bD4tm2xZV.cW/iijCWRaZyq0ZZrjO', 'ROLE_ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('manager', 'manager@orderflow.local', '$2b$12$mIh29dX0CalskJoWWdNVteffxQq0bgnT08tYIFrXxPmR1bggCj1jG', 'ROLE_MANAGER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('customer1', 'customer1@orderflow.local', '$2b$12$7NZObHMvIk39k/IO8uTGKeqBOMq67vt1PdfjPIwiUDQmoc63fS7DS', 'ROLE_CUSTOMER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('customer2', 'customer2@orderflow.local', '$2b$12$7NZObHMvIk39k/IO8uTGKeqBOMq67vt1PdfjPIwiUDQmoc63fS7DS', 'ROLE_CUSTOMER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (username) DO NOTHING;

INSERT INTO products (sku, name, description, price, stock_quantity, category, active, version, created_at, updated_at)
VALUES
    ('PROD-MACBOOK-M3', 'Apple MacBook Pro M3', '14-inch Apple M3 chip with 16GB unified memory and 512GB SSD storage.', 1999.00, 25, 'Electronics', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PROD-DELL-XPS15', 'Dell XPS 15 Laptop', '15.6-inch OLED 3.5K display, Intel Core i7, 32GB RAM, 1TB SSD.', 1499.00, 30, 'Electronics', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PROD-LOGI-MX3S', 'Logitech MX Master 3S', 'Advanced wireless performance mouse with 8K DPI tracking and quiet clicks.', 99.00, 150, 'Accessories', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PROD-KBD-MECH-PRO', 'Keychron K2 Mechanical Keyboard', 'Wireless Bluetooth/USB mechanical keyboard with hot-swappable switches.', 129.00, 80, 'Accessories', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PROD-SONY-WH1000', 'Sony WH-1000XM5 Headphones', 'Industry leading noise canceling wireless headphones with premium sound.', 349.00, 45, 'Electronics', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PROD-DESK-STAND', 'Ergonomic Standing Desk', 'Dual motor electric height adjustable desk frame with bamboo desktop.', 499.00, 20, 'Office', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PROD-CHAIR-ERGO', 'Ergonomic Mesh Chair', 'High-back desk chair with dynamic lumbar support and 3D armrests.', 299.00, 40, 'Office', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PROD-MONITOR-4K', 'Dell UltraSharp 27 4K Monitor', '27-inch 4K UHD IPS USB-C Hub Monitor with 99% sRGB color gamut.', 549.00, 35, 'Electronics', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('PROD-LIMITED-FLASH', 'Limited Edition Smart Watch Band', 'Silicone sports band with quick-release magnetic clasp for flash sales.', 19.99, 50, 'Accessories', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (sku) DO NOTHING;
