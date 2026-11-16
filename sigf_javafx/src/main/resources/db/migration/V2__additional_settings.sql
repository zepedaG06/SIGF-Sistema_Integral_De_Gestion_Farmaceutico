INSERT INTO setting(key,value) VALUES ('business_details','Sin datos comerciales'),('expiry_days','30') ON CONFLICT (key) DO NOTHING;
