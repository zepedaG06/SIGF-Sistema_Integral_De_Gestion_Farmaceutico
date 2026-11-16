CREATE TABLE sale_request (
 request_id uuid PRIMARY KEY, actor_id bigint NOT NULL REFERENCES app_user,
 customer_id bigint REFERENCES customer, method varchar(20) NOT NULL CHECK(method IN ('CASH','CARD','TRANSFER')),
 expected_total numeric(14,2) NOT NULL CHECK(expected_total >= 0),
 created_at timestamptz NOT NULL DEFAULT now(), acknowledged_at timestamptz
);
CREATE TABLE sale_request_line (
 request_id uuid NOT NULL REFERENCES sale_request, product_id bigint NOT NULL REFERENCES product,
 quantity integer NOT NULL CHECK(quantity > 0), PRIMARY KEY(request_id, product_id)
);
CREATE INDEX sale_request_unack ON sale_request(actor_id,created_at) WHERE acknowledged_at IS NULL;
