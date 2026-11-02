-- Minimal H2-only subset for Sales Assignment open-work queries.
CREATE TABLE leads (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id),
 owner_user_id UUID NOT NULL REFERENCES users(id), offering_id UUID NOT NULL REFERENCES offerings(id),
 display_name VARCHAR(180) NOT NULL, email VARCHAR(254), normalized_email VARCHAR(254), status VARCHAR(30) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 archived_at TIMESTAMP WITH TIME ZONE
);
CREATE TABLE opportunities (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id),
 lead_id UUID REFERENCES leads(id), offering_id UUID NOT NULL REFERENCES offerings(id),
 owner_user_id UUID NOT NULL REFERENCES users(id), title VARCHAR(220) NOT NULL,
 stage VARCHAR(30) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE crm_tasks (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id),
 lead_id UUID REFERENCES leads(id), opportunity_id UUID REFERENCES opportunities(id),
 owner_user_id UUID NOT NULL REFERENCES users(id), created_by_user_id UUID NOT NULL REFERENCES users(id),
 title VARCHAR(220) NOT NULL, priority VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
 due_at TIMESTAMP WITH TIME ZONE NOT NULL, origin VARCHAR(30) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
