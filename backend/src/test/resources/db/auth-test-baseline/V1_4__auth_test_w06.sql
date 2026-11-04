-- H2 fixture extension for W06 APIs; production schema comes from V1 plus V11.
ALTER TABLE crm_tasks ADD COLUMN customer_id UUID;
ALTER TABLE crm_tasks ADD COLUMN description TEXT;
ALTER TABLE crm_tasks ADD COLUMN result TEXT;
ALTER TABLE crm_tasks ADD COLUMN idempotency_key VARCHAR(160);
ALTER TABLE crm_tasks ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE interactions (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id),
 customer_id UUID, lead_id UUID, opportunity_id UUID,
 recorded_by_user_id UUID NOT NULL REFERENCES users(id), type VARCHAR(50) NOT NULL,
 channel VARCHAR(50) NOT NULL, subject VARCHAR(220) NOT NULL, content TEXT NOT NULL,
 outcome TEXT, visibility VARCHAR(20) NOT NULL,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CONSTRAINT ck_interaction_subject_xor CHECK ((customer_id IS NOT NULL) <> (lead_id IS NOT NULL)),
 CONSTRAINT ck_interaction_visibility CHECK (visibility IN ('CARE_VISIBLE','SALES_ONLY'))
);
