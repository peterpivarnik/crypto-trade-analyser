CREATE SEQUENCE seq_statistic_id START 1 INCREMENT BY 300;

CREATE TABLE statistic (
    id BIGINT NOT NULL DEFAULT nextval('seq_statistic_id'),
    created_at BIGINT,
    probability_2h NUMERIC(20, 8),
    probability_5h NUMERIC(20, 8),

    CONSTRAINT pk_statistic_id PRIMARY KEY (id)
);