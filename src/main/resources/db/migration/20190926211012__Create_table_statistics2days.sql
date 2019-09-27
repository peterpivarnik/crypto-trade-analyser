CREATE SEQUENCE seq_statistic_2day_id START 1 INCREMENT BY 300;

CREATE TABLE statistic_2day (
    id BIGINT NOT NULL DEFAULT nextval('seq_statistic_2day_id'),
    created_at BIGINT,
    created_at_date TIMESTAMP,
    success_rate NUMERIC(20, 8),

    CONSTRAINT pk_statistic_2day_id PRIMARY KEY (id)
);