ALTER TABLE purchase ADD COLUMN request_id uuid UNIQUE;
CREATE TABLE purchase_request (
 request_id uuid PRIMARY KEY,
 actor_id bigint NOT NULL REFERENCES app_user,
 supplier_id bigint REFERENCES supplier,
 created_at timestamptz NOT NULL DEFAULT now(),
 acknowledged_at timestamptz,
 cancelled boolean NOT NULL DEFAULT false
);
CREATE TABLE purchase_request_line (
 request_id uuid NOT NULL REFERENCES purchase_request,
 line_no integer NOT NULL,
 product_id bigint NOT NULL REFERENCES product,
 lot varchar(100) NOT NULL,
 expires_on date NOT NULL,
 quantity integer NOT NULL CHECK(quantity > 0),
 unit_cost numeric(14,2) NOT NULL CHECK(unit_cost >= 0),
 PRIMARY KEY(request_id,line_no)
);
CREATE INDEX purchase_request_unack ON purchase_request(actor_id,created_at) WHERE acknowledged_at IS NULL;
ALTER TABLE sale_line ADD COLUMN product_name_snapshot varchar(200);
CREATE TABLE sale_receipt_snapshot (
 sale_id bigint PRIMARY KEY REFERENCES sale,
 pharmacy_name varchar(160) NOT NULL,
 legal_name varchar(200) NOT NULL,
 ruc varchar(30) NOT NULL,
 address varchar(300) NOT NULL,
 phone varchar(60) NOT NULL,
 email varchar(160) NOT NULL,
 customer_name varchar(200) NOT NULL,
 currency varchar(20) NOT NULL
);
