-- Internal classroom series; this is not a DGI authorization.
CREATE TABLE internal_series (id smallint PRIMARY KEY CHECK(id=1), code varchar(20) NOT NULL, next_number bigint NOT NULL CHECK(next_number>0));
INSERT INTO internal_series VALUES(1,'DEMO',1);
CREATE TABLE internal_document (sale_id bigint PRIMARY KEY REFERENCES sale, series varchar(20) NOT NULL, number bigint NOT NULL CHECK(number>0), UNIQUE(series,number));
ALTER TABLE sale ADD COLUMN voided_at timestamptz;
ALTER TABLE sale ADD COLUMN voided_by bigint REFERENCES app_user;
ALTER TABLE sale ADD COLUMN void_reason text;
ALTER TABLE customer ADD COLUMN ruc varchar(30) NOT NULL DEFAULT '';
ALTER TABLE sale_receipt_snapshot ADD COLUMN customer_ruc varchar(30) NOT NULL DEFAULT '';
