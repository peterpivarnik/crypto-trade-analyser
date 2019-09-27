CREATE SEQUENCE seq_statistic_week_id START 1 INCREMENT BY 300;

CREATE TABLE statistic_week (
    id BIGINT NOT NULL DEFAULT nextval('seq_statistic_week_id'),
    created_at BIGINT,
    created_at_date TIMESTAMP,
    success_rate NUMERIC(20, 8),

    CONSTRAINT pk_statistic_week_id PRIMARY KEY (id)
);