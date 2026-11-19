CREATE TABLE product_catalog (
  kind varchar(20) NOT NULL CHECK (kind IN ('category','laboratory')),
  name varchar(120) NOT NULL CHECK (length(trim(name)) > 0),
  PRIMARY KEY (kind, name)
);
INSERT INTO product_catalog(kind,name)
SELECT 'category', trim(category) FROM product WHERE category IS NOT NULL AND trim(category) <> '' GROUP BY trim(category)
UNION ALL
SELECT 'laboratory', trim(laboratory) FROM product WHERE laboratory IS NOT NULL AND trim(laboratory) <> '' GROUP BY trim(laboratory)
ON CONFLICT DO NOTHING;
