ALTER TABLE statistic DROP COLUMN probability;
ALTER TABLE statistic DROP COLUMN valid_cryptos;
ALTER TABLE statistic DROP COLUMN all_cryptos;
ALTER TABLE statistic ADD COLUMN success_rate NUMERIC(20,8) DEFAULT 0;