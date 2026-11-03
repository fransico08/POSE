ALTER TABLE public.opportunities ADD CONSTRAINT uq_opportunities_organization_id_id UNIQUE (organization_id, id);

ALTER TABLE public.opportunities ADD COLUMN final_value NUMERIC(18,2) CHECK (final_value >= 0);
ALTER TABLE public.opportunities ADD COLUMN actual_close_date DATE;
ALTER TABLE public.opportunities ADD COLUMN notes TEXT;

CREATE TABLE public.opportunity_lines (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    opportunity_id UUID NOT NULL,
    offering_id UUID NOT NULL,
    position INTEGER NOT NULL,
    quantity NUMERIC(12,2) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(18,2) NOT NULL CHECK (unit_price >= 0),
    discount_amount NUMERIC(18,2) DEFAULT 0 NOT NULL CHECK (discount_amount >= 0),
    line_total NUMERIC(18,2) NOT NULL CHECK (line_total >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT opportunity_lines_opportunity_fkey FOREIGN KEY (organization_id, opportunity_id)
        REFERENCES public.opportunities (organization_id, id),
    CONSTRAINT opportunity_lines_offering_fkey FOREIGN KEY (organization_id, offering_id)
        REFERENCES public.offerings (organization_id, id),
    CONSTRAINT uq_opportunity_lines_position UNIQUE (opportunity_id, position),
    CONSTRAINT uq_opportunity_lines_offering UNIQUE (opportunity_id, offering_id),
    CONSTRAINT ck_opportunity_line_discount CHECK (discount_amount <= quantity * unit_price)
);
CREATE INDEX ix_opportunity_lines_org_offering ON public.opportunity_lines (organization_id, offering_id);

-- Every legacy opportunity gets one line. Reusing its UUID in the separate
-- line table is deterministic and requires no database-specific UUID function.
INSERT INTO public.opportunity_lines
    (id, organization_id, opportunity_id, offering_id, position, quantity, unit_price, discount_amount, line_total, created_at, updated_at)
SELECT id, organization_id, id, offering_id, 1, 1, COALESCE(expected_value, 0), 0,
       COALESCE(expected_value, 0), created_at, updated_at
FROM public.opportunities;

ALTER TABLE public.opportunities DROP CONSTRAINT IF EXISTS ck_opportunity_stage;
ALTER TABLE public.opportunities DROP CONSTRAINT IF EXISTS ck_opportunity_closed;

UPDATE public.opportunities
SET final_value = expected_value,
    actual_close_date = CAST(closed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS DATE)
WHERE stage = 'WON';

ALTER TABLE public.opportunities ADD CONSTRAINT ck_opportunity_stage
    CHECK (stage IN ('NEW','QUALIFIED','PROPOSAL','WON','LOST','CANCELLED'));
ALTER TABLE public.opportunities ADD CONSTRAINT ck_opportunity_closed CHECK (
    (stage = 'WON' AND closed_at IS NOT NULL AND final_value IS NOT NULL AND actual_close_date IS NOT NULL)
    OR (stage IN ('LOST','CANCELLED') AND closed_at IS NOT NULL AND closing_reason IS NOT NULL)
    OR (stage IN ('NEW','QUALIFIED','PROPOSAL') AND closed_at IS NULL AND final_value IS NULL AND actual_close_date IS NULL)
);
