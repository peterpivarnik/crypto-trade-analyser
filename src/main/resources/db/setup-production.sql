CREATE ROLE cta WITH PASSWORD 'cta123' NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT LOGIN;
CREATE DATABASE cta WITH OWNER cta ENCODING 'UTF8' LC_COLLATE = 'en_US.UTF-8' LC_CTYPE = 'en_US.UTF-8';
