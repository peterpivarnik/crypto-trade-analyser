CREATE SEQUENCE seq_crypto_id START 1 INCREMENT BY 50;

CREATE TABLE crypto (
    id BIGINT NOT NULL DEFAULT nextval('seq_crypto_id'),
    symbol VARCHAR(10) NOT NULL,
    current_price NUMERIC(20, 8),
    fifteen_minutes_max_to_current_different NUMERIC(20, 8),
    fifteen_minutes_percentage_loss NUMERIC(20, 8),
    last_three_days_average_price NUMERIC(20, 8),
    last_three_days_max_price NUMERIC(20, 8),
    last_three_days_min_price NUMERIC(20, 8),
    last_three_days_max_min_different_percent NUMERIC(20, 8),
    volume NUMERIC(20, 8),
    weight NUMERIC(20, 8),
    ratio NUMERIC(20, 8),
    price_to_sell NUMERIC(20, 8),
    created_at TIMESTAMP,
    next_day_max_value NUMERIC(20,8) DEFAULT 0,

    CONSTRAINT pk_crypto_id PRIMARY KEY (id)
);

COMMENT ON TABLE crypto IS 'Crypto data';
COMMENT ON COLUMN crypto.id IS 'Primary key for crypto table';

