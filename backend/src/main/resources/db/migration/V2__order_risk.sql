-- Risk score from the risk service, recorded at checkout. NULL when the service was off or did not answer.
ALTER TABLE orders ADD COLUMN risk_score INT;
ALTER TABLE orders ADD COLUMN risk_level VARCHAR(10) CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH'));
ALTER TABLE orders ADD COLUMN risk_reasons VARCHAR(200);
CREATE INDEX ix_orders_risk_level ON orders (risk_level) WHERE risk_level = 'HIGH';
