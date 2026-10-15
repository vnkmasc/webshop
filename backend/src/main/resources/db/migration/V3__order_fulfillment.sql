-- Hand-over to the warehouse through the order event queue. NULL when the queue is not configured.
ALTER TABLE orders ADD COLUMN fulfillment_status VARCHAR(12)
    CHECK (fulfillment_status IN ('QUEUED', 'RECEIVED', 'SEND_FAILED'));
ALTER TABLE orders ADD COLUMN fulfillment_at TIMESTAMPTZ;
CREATE INDEX ix_orders_fulfillment_queued ON orders (created_at) WHERE fulfillment_status = 'QUEUED';
