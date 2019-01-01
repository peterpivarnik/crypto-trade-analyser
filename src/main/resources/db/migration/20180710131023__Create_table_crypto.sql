CREATE SEQUENCE seq_crypto_id START 1 INCREMENT BY 300;

CREATE TABLE crypto (
    id BIGINT NOT NULL DEFAULT nextval('seq_crypto_id'),
    symbol VARCHAR(10) NOT NULL,
    current_price NUMERIC(20, 8),
    volume NUMERIC(20, 8),
    sum_diff_percent NUMERIC(20, 8),
    sum_diff_percent10h NUMERIC(20, 8),
    price_to_sell NUMERIC(20, 8),
    price_to_sell_percentage NUMERIC(20, 8),
    weight NUMERIC(20, 8),
    created_at BIGINT,
    next_day_max_price NUMERIC(20,8) DEFAULT 0,

    CONSTRAINT pk_crypto_id PRIMARY KEY (id)
);