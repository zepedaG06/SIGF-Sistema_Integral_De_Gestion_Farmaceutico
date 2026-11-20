CREATE TABLE purchase_draft (
 actor_id bigint PRIMARY KEY REFERENCES app_user,
 supplier_id bigint REFERENCES supplier,
 request_id uuid,
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE purchase_draft_line (
 actor_id bigint NOT NULL REFERENCES purchase_draft(actor_id) ON DELETE CASCADE,
 line_no integer NOT NULL,
 product_id bigint NOT NULL REFERENCES product,
 lot varchar(100) NOT NULL,
 expires_on date NOT NULL,
 quantity integer NOT NULL CHECK(quantity>0),
 cost numeric(14,2) NOT NULL CHECK(cost>=0),
 PRIMARY KEY(actor_id,line_no)
);
