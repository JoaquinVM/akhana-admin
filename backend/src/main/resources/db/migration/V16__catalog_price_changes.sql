-- CAT-07: a product created from the catalog records its first cost and price with no previous values
ALTER TABLE product_price_changes
    ALTER COLUMN previous_buy_price DROP NOT NULL,
    ALTER COLUMN previous_sell_price DROP NOT NULL;
