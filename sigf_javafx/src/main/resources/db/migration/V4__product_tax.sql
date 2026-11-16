-- Existing products require explicit tax classification; legacy sale totals remain unchanged.
ALTER TABLE product ADD COLUMN tax_rate numeric(6,5) CHECK (tax_rate BETWEEN 0 AND 1);
ALTER TABLE sale_line ADD COLUMN tax_rate numeric(6,5) CHECK (tax_rate BETWEEN 0 AND 1);
