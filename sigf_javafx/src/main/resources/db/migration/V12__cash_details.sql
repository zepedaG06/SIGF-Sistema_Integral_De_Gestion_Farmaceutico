ALTER TABLE sale_request ADD COLUMN received_amount numeric(14,2) CHECK(received_amount>=expected_total);
ALTER TABLE payment ADD COLUMN received_amount numeric(14,2);
ALTER TABLE payment ADD COLUMN change_amount numeric(14,2);
ALTER TABLE payment ADD CONSTRAINT payment_cash_detail CHECK ((received_amount IS NULL AND change_amount IS NULL) OR (received_amount IS NOT NULL AND change_amount IS NOT NULL AND received_amount>=amount AND change_amount=received_amount-amount));
