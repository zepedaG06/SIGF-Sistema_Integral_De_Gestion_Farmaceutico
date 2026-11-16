ALTER TABLE sale_request_line ADD COLUMN quoted_price numeric(14,2) CHECK(quoted_price >= 0);
ALTER TABLE sale_request_line ADD COLUMN quoted_tax_rate numeric(6,5) CHECK(quoted_tax_rate BETWEEN 0 AND 1);
