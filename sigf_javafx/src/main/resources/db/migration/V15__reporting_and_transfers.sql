CREATE TABLE bank (name varchar(100) PRIMARY KEY, active boolean NOT NULL DEFAULT true);
INSERT INTO bank(name) VALUES ('BAC'),('LAFISE'),('BANPRO'),('BDF'),('FICOHSA'),('AVANZ');
ALTER TABLE sale_request ADD COLUMN bank_name varchar(100) REFERENCES bank(name), ADD COLUMN transfer_reference varchar(120), ADD COLUMN transfer_description varchar(500);
ALTER TABLE payment ADD COLUMN bank_name varchar(100) REFERENCES bank(name), ADD COLUMN transfer_reference varchar(120), ADD COLUMN transfer_description varchar(500);
ALTER TABLE sale_request ADD CONSTRAINT request_transfer_fields CHECK ((bank_name IS NULL AND transfer_reference IS NULL AND transfer_description IS NULL) OR (method='TRANSFER' AND bank_name IS NOT NULL AND length(trim(transfer_reference))>0));
ALTER TABLE payment ADD CONSTRAINT payment_transfer_fields CHECK ((bank_name IS NULL AND transfer_reference IS NULL AND transfer_description IS NULL) OR (method='TRANSFER' AND bank_name IS NOT NULL AND length(trim(transfer_reference))>0));
CREATE INDEX payment_transfer_lookup ON payment(bank_name,transfer_reference) WHERE method='TRANSFER';
CREATE TABLE purchase_snapshot (purchase_id bigint PRIMARY KEY REFERENCES purchase(id), supplier_name text NOT NULL, supplier_ruc text NOT NULL, actor_name text NOT NULL);
-- No se inventan instantáneas de documentos anteriores.

ALTER TABLE sale_line ADD COLUMN presentation_snapshot text;
ALTER TABLE purchase_line ADD COLUMN product_name_snapshot text, ADD COLUMN presentation_snapshot text;
ALTER TABLE sale_receipt_snapshot ADD COLUMN seller_name text;
