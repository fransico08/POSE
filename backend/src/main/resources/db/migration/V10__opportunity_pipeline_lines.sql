ALTER TABLE public.opportunities ADD COLUMN notes text;
ALTER TABLE public.opportunities ADD COLUMN actual_value numeric(18,2);
ALTER TABLE public.opportunities ADD COLUMN actual_close_date date;
ALTER TABLE public.opportunities
    ADD CONSTRAINT ck_opportunity_actual_value CHECK (actual_value IS NULL OR actual_value >= 0);
ALTER TABLE public.opportunities
    ADD CONSTRAINT ck_opportunity_actual_close_pair CHECK (
        (actual_value IS NULL AND actual_close_date IS NULL)
        OR (stage = 'WON' AND actual_value IS NOT NULL AND actual_close_date IS NOT NULL)
    );
ALTER TABLE public.opportunities
    ADD CONSTRAINT uq_opportunities_organization_id_id UNIQUE (organization_id, id);

CREATE TABLE public.opportunity_lines (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    opportunity_id uuid NOT NULL,
    line_number integer NOT NULL,
    offering_id uuid NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(18,2),
    discount_percent numeric(5,2) NOT NULL DEFAULT 0,
    line_value numeric(18,2),
    legacy_value_only boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT opportunity_lines_pkey PRIMARY KEY (id),
    CONSTRAINT uq_opportunity_lines_org_opportunity_line UNIQUE (organization_id, opportunity_id, line_number),
    CONSTRAINT ck_opportunity_lines_line_number CHECK (line_number > 0),
    CONSTRAINT ck_opportunity_lines_quantity CHECK (quantity > 0),
    CONSTRAINT ck_opportunity_lines_discount CHECK (discount_percent >= 0 AND discount_percent <= 100),
    CONSTRAINT ck_opportunity_lines_pricing CHECK (
        (legacy_value_only = true AND unit_price IS NULL)
        OR (legacy_value_only = false AND unit_price IS NOT NULL AND unit_price >= 0 AND line_value IS NOT NULL)
    ),
    CONSTRAINT ck_opportunity_lines_value CHECK (line_value IS NULL OR line_value >= 0),
    CONSTRAINT opportunity_lines_opportunity_scope_fkey
        FOREIGN KEY (organization_id, opportunity_id)
        REFERENCES public.opportunities (organization_id, id) ON DELETE RESTRICT,
    CONSTRAINT opportunity_lines_offering_scope_fkey
        FOREIGN KEY (organization_id, offering_id)
        REFERENCES public.offerings (organization_id, id) ON DELETE RESTRICT
);

CREATE INDEX ix_opportunity_lines_org_offering
    ON public.opportunity_lines (organization_id, offering_id, opportunity_id);
CREATE INDEX ix_opportunity_lines_org_opportunity
    ON public.opportunity_lines (organization_id, opportunity_id, line_number);

-- The former model had one Offering and only an aggregate expected value. Keep that value
-- exactly, without pretending we know its original quantity, unit price, or discount.
INSERT INTO public.opportunity_lines (
    id, organization_id, opportunity_id, line_number, offering_id, quantity,
    unit_price, discount_percent, line_value, legacy_value_only, created_at, updated_at
)
SELECT o.id, o.organization_id, o.id, 1, o.offering_id, 1,
       NULL, 0, o.expected_value, true, o.created_at, o.updated_at
FROM public.opportunities o;

COMMENT ON TABLE public.opportunity_lines IS 'Offering lines for a sales opportunity. Legacy rows preserve the former aggregate value without inventing unit pricing.';
COMMENT ON COLUMN public.opportunity_lines.legacy_value_only IS 'True when migrated from the former single-offering model; unit price and discount breakdown were not present in source data.';
COMMENT ON COLUMN public.opportunities.offering_id IS 'Legacy primary Offering reference. New pipeline data is represented by opportunity_lines.';
