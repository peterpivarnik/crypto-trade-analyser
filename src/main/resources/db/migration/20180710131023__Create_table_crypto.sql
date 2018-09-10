CREATE SEQUENCE seq_crypto_id START 1 INCREMENT BY 300;

CREATE TABLE crypto (
    id BIGINT NOT NULL DEFAULT nextval('seq_crypto_id'),
    symbol VARCHAR(10) NOT NULL,
    current_price NUMERIC(20, 8),
    volume NUMERIC(20, 8),
    sum_diff_percent_2h NUMERIC(20, 8),
    sum_diff_percent_5h NUMERIC(20, 8),
    sum_diff_percent_10h NUMERIC(20, 8),
    sum_diff_percent_24h NUMERIC(20, 8),
    price_to_sell_2h NUMERIC(20, 8),
    price_to_sell_5h NUMERIC(20, 8),
    price_to_sell_10h NUMERIC(20, 8),
    price_to_sell_24h NUMERIC(20, 8),
    price_to_sell_percentage_2h NUMERIC(20, 8),
    price_to_sell_percentage_5h NUMERIC(20, 8),
    price_to_sell_percentage_10h NUMERIC(20, 8),
    price_to_sell_percentage_24h NUMERIC(20, 8),
    weight_2h NUMERIC(20, 8),
    weight_5h NUMERIC(20, 8),
    weight_10h NUMERIC(20, 8),
    weight_24h NUMERIC(20, 8),
    crypto_type VARCHAR(15),
    created_at TIMESTAMP,
    next_day_max_price NUMERIC(20,8) DEFAULT 0,

    CONSTRAINT pk_crypto_id PRIMARY KEY (id)
);