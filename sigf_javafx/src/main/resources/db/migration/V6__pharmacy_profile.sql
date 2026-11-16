CREATE TABLE pharmacy_profile (
 id smallint PRIMARY KEY CHECK(id=1),
 pharmacy_name varchar(160) NOT NULL CHECK(length(trim(pharmacy_name))>0),
 legal_name varchar(200) NOT NULL DEFAULT '',
 ruc varchar(30) NOT NULL DEFAULT '',
 address varchar(300) NOT NULL DEFAULT '',
 phone varchar(60) NOT NULL DEFAULT '',
 email varchar(160) NOT NULL DEFAULT '',
 logo bytea CHECK(logo IS NULL OR octet_length(logo) <= 2097152),
 updated_by bigint NOT NULL REFERENCES app_user(id),
 updated_at timestamptz NOT NULL DEFAULT now()
);
