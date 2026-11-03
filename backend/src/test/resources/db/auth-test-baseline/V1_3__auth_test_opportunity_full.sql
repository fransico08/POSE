-- H2-only additions matching the opportunity fields used by V10 and W05.
ALTER TABLE opportunities ADD COLUMN customer_id UUID REFERENCES customers(id);
ALTER TABLE opportunities ADD COLUMN expected_value NUMERIC(18,2);
ALTER TABLE opportunities ADD COLUMN currency_code CHAR(3) DEFAULT 'VND' NOT NULL;
ALTER TABLE opportunities ADD COLUMN expected_close_date DATE;
ALTER TABLE opportunities ADD COLUMN closing_reason TEXT;
ALTER TABLE opportunities ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE opportunities ADD CONSTRAINT ck_opportunity_stage CHECK (stage IN ('NEW','QUALIFIED','PROPOSAL','WON','LOST'));
ALTER TABLE opportunities ADD CONSTRAINT ck_opportunity_closed CHECK (
    (stage IN ('WON','LOST') AND closing_reason IS NOT NULL AND closed_at IS NOT NULL)
    OR (stage NOT IN ('WON','LOST') AND closed_at IS NULL)
);
ALTER TABLE opportunities ADD CONSTRAINT ck_opportunity_subject_xor CHECK ((customer_id IS NOT NULL) <> (lead_id IS NOT NULL));
ALTER TABLE opportunities ADD CONSTRAINT ck_opportunity_value CHECK (expected_value IS NULL OR expected_value >= 0);
