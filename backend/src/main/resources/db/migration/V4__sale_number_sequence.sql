-- T-06: sale numbers come from a database sequence and are unique,
-- instead of "sales in the cash session + 1" (two concurrent sales got the same number).

CREATE SEQUENCE sale_number_seq;

-- Old numbers (VTA-<session>-<count>) may repeat: the first keeps its number, the rest get a suffix.
-- New numbers use another format (VTA-<sequence>) and cannot collide with old ones.
UPDATE sales s
SET sale_number = s.sale_number || '-' || d.rn
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY sale_number ORDER BY created_at, id) AS rn
    FROM sales
) d
WHERE s.id = d.id AND d.rn > 1;

CREATE UNIQUE INDEX uq_sales_sale_number ON sales (sale_number);
