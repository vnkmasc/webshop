CREATE TABLE payments (
    reference     VARCHAR(30)  PRIMARY KEY,
    order_code    VARCHAR(30)  NOT NULL,
    amount        NUMERIC(14, 0) NOT NULL,
    currency      VARCHAR(3)   NOT NULL,
    card_brand    VARCHAR(20)  NOT NULL,
    card_last4    VARCHAR(4)   NOT NULL,
    status        VARCHAR(10)  NOT NULL CHECK (status IN ('APPROVED', 'DECLINED', 'REFUNDED')),
    reason        VARCHAR(200),
    processing_ms INT          NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    refunded_at   TIMESTAMPTZ
);
CREATE INDEX ix_payments_order ON payments (order_code);
CREATE INDEX ix_payments_created ON payments (created_at DESC);
