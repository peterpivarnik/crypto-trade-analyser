CREATE SEQUENCE seq_crypto_1H_id START 1 INCREMENT BY 300;
CREATE SEQUENCE seq_crypto_2H_id START 1 INCREMENT BY 300;
CREATE SEQUENCE seq_crypto_5H_id START 1 INCREMENT BY 300;

CREATE TABLE crypto_1H (
    id BIGINT NOT NULL DEFAULT nextval('seq_crypto_1H_id'),
    symbol VARCHAR(10) NOT NULL,
    current_price NUMERIC(20, 8),
    volume NUMERIC(20, 8),
    sum_diff_percent_1day NUMERIC(20, 8),
    sum_diff_percent NUMERIC(20, 8),
    price_to_sell NUMERIC(20, 8),
    price_to_sell_percentage NUMERIC(20, 8),
    weight NUMERIC(20, 8),
    crypto_type VARCHAR(15),
    created_at BIGINT,
    next_day_max_price NUMERIC(20,8) DEFAULT 0,

    CONSTRAINT pk_crypto_1H_id PRIMARY KEY (id)
);

CREATE TABLE crypto_2H (
    id BIGINT NOT NULL DEFAULT nextval('seq_crypto_2H_id'),
    symbol VARCHAR(10) NOT NULL,
    current_price NUMERIC(20, 8),
    volume NUMERIC(20, 8),
    sum_diff_percent_1day NUMERIC(20, 8),
    sum_diff_percent NUMERIC(20, 8),
    price_to_sell NUMERIC(20, 8),
    price_to_sell_percentage NUMERIC(20, 8),
    weight NUMERIC(20, 8),
    crypto_type VARCHAR(15),
    created_at BIGINT,
    next_day_max_price NUMERIC(20,8) DEFAULT 0,

    CONSTRAINT pk_crypto_2H_id PRIMARY KEY (id)
);

CREATE TABLE crypto_5H (
    id BIGINT NOT NULL DEFAULT nextval('seq_crypto_5H_id'),
    symbol VARCHAR(10) NOT NULL,
    current_price NUMERIC(20, 8),
    volume NUMERIC(20, 8),
    sum_diff_percent_1day NUMERIC(20, 8),
    sum_diff_percent NUMERIC(20, 8),
    price_to_sell NUMERIC(20, 8),
    price_to_sell_percentage NUMERIC(20, 8),
    weight NUMERIC(20, 8),
    crypto_type VARCHAR(15),
    created_at BIGINT,
    next_day_max_price NUMERIC(20,8) DEFAULT 0,

    CONSTRAINT pk_crypto_5H_id PRIMARY KEY (id)
);
