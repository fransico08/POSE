-- H2-only CRM test subset; production uses the restored baseline instead.
CREATE TABLE customers (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id),
 owner_user_id UUID NOT NULL REFERENCES users(id), display_name VARCHAR(180) NOT NULL,
 email VARCHAR(254), normalized_email VARCHAR(254), phone VARCHAR(40), normalized_phone VARCHAR(40),
 status VARCHAR(30) NOT NULL, metadata JSONB DEFAULT '{}' NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, archived_at TIMESTAMP WITH TIME ZONE,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 CONSTRAINT ck_customers_identity CHECK (normalized_email IS NOT NULL OR normalized_phone IS NOT NULL),
 UNIQUE(organization_id,normalized_email), UNIQUE(organization_id,normalized_phone)
);
CREATE TABLE customer_assignments (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id),
 customer_id UUID NOT NULL REFERENCES customers(id), user_id UUID NOT NULL REFERENCES users(id),
 assignment_type VARCHAR(20) NOT NULL CHECK (assignment_type IN ('SALES','CARE')),
 effective_from TIMESTAMP WITH TIME ZONE NOT NULL, effective_to TIMESTAMP WITH TIME ZONE,
 status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','ENDED')),
 assigned_by_user_id UUID NOT NULL REFERENCES users(id),
 source VARCHAR(30) NOT NULL CHECK (source IN ('INITIAL','MANUAL','HANDOVER','IMPORT')),
 handover_request_id UUID, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 active_assignment_type VARCHAR(20) GENERATED ALWAYS AS (CASE WHEN status='ACTIVE' THEN assignment_type ELSE NULL END),
 CHECK (effective_to IS NULL OR effective_to>=effective_from), UNIQUE(customer_id,active_assignment_type)
);
ALTER TABLE leads ADD COLUMN phone VARCHAR(40);
ALTER TABLE leads ADD COLUMN normalized_phone VARCHAR(40);
ALTER TABLE leads ADD COLUMN qualified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE leads ADD COLUMN converted_customer_id UUID REFERENCES customers(id);
ALTER TABLE leads ADD COLUMN converted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE leads ADD COLUMN disqualification_reason TEXT;
ALTER TABLE leads ADD CONSTRAINT ck_leads_identity CHECK (normalized_email IS NOT NULL OR normalized_phone IS NOT NULL);
ALTER TABLE leads ADD CONSTRAINT ck_leads_status CHECK (status IN ('NEW','QUALIFIED','CONVERTED','DISQUALIFIED'));
ALTER TABLE leads ADD CONSTRAINT ck_leads_conversion CHECK ((status='CONVERTED' AND converted_customer_id IS NOT NULL AND converted_at IS NOT NULL) OR (status<>'CONVERTED' AND converted_customer_id IS NULL AND converted_at IS NULL));
ALTER TABLE leads ADD CONSTRAINT ck_leads_disqualified CHECK (status<>'DISQUALIFIED' OR disqualification_reason IS NOT NULL);
