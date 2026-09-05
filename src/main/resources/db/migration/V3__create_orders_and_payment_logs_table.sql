-- Migration V3: Create orders and payment_transaction_logs tables
-- Tabel pesanan utama / Main orders table
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(100) NOT NULL UNIQUE,
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_orders_order_number ON orders(order_number);
CREATE INDEX idx_orders_status ON orders(status);

-- Tabel log audit webhook payment / Webhook audit transaction log table
CREATE TABLE payment_transaction_logs (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL,
    order_number VARCHAR(100) NOT NULL,
    payment_type VARCHAR(50),
    gross_amount NUMERIC(19, 2),
    transaction_status VARCHAR(50) NOT NULL,
    raw_payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_logs_transaction_id ON payment_transaction_logs(transaction_id);
CREATE INDEX idx_logs_order_number ON payment_transaction_logs(order_number);
