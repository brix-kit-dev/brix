-- An order is PLACED until a sample-orders.cancel-order command cancels it (standard-link plan L4.7).
ALTER TABLE sample_orders_order ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PLACED';
ALTER TABLE sample_orders_order ADD COLUMN cancel_reason VARCHAR(32);
ALTER TABLE sample_orders_order ADD CONSTRAINT chk_sample_orders_order_status CHECK (status IN ('PLACED', 'CANCELLED'));
