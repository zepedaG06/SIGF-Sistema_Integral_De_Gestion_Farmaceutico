ALTER TABLE app_user ADD COLUMN session_version uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE batch ADD CONSTRAINT batch_product_identity UNIQUE(id,product_id);
ALTER TABLE sale_line ADD CONSTRAINT sale_line_batch_product FOREIGN KEY(batch_id,product_id) REFERENCES batch(id,product_id) NOT VALID;
ALTER TABLE sale ADD CONSTRAINT sale_amounts_consistent CHECK(subtotal>=0 AND tax>=0 AND total=subtotal+tax) NOT VALID;
ALTER TABLE payment ADD CONSTRAINT payment_method_valid CHECK(method IN ('CASH','CARD','TRANSFER')) NOT VALID;
CREATE INDEX purchase_time_idx ON purchase(created_at);
CREATE INDEX sale_line_sale_idx ON sale_line(sale_id);
CREATE INDEX purchase_line_purchase_idx ON purchase_line(purchase_id);
