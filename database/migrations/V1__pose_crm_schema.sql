-- POSE CRM - Migration V1 (synchronized review-ready baseline)
-- Hard baseline: latest repo Decision Log DEC-01..DEC-05, Roles & Authorization v1,
-- Architecture v1 and API Contract Foundation v1.
-- Review context (provisional, not schema lock): Business Rules v1 / Domain Class Diagram v1.
-- Review defects L01-L07 / D01-D10 are addressed by the paired seed/validator.
-- Target: PostgreSQL 16+/17. Run the included verification script on the local PostgreSQL instance before merge.
-- 36 physical tables + 1 Customer 360 read-model view.

BEGIN;

CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    industry VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_organizations_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE departments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_departments_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_departments_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    code VARCHAR(40) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_roles_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_roles_code CHECK (code IN ('ADMIN','MANAGER','SALES_REP','CUSTOMER_CARE','DATA_STAFF')),
    CONSTRAINT ck_roles_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    full_name VARCHAR(160) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    email VARCHAR(254) NOT NULL,
    normalized_email VARCHAR(254) NOT NULL,
    archived_at TIMESTAMPTZ,
    CONSTRAINT uq_users_org_email UNIQUE (organization_id, normalized_email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED'))
);

CREATE TABLE credentials (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
    password_hash VARCHAR(255) NOT NULL,
    password_changed_at TIMESTAMPTZ NOT NULL,
    failed_login_count INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_credentials_failures CHECK (failed_login_count >= 0)
);

CREATE TABLE teams (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    manager_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    CONSTRAINT uq_teams_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_teams_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE team_memberships (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    team_id UUID NOT NULL REFERENCES teams(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    joined_at TIMESTAMPTZ NOT NULL,
    left_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_team_membership UNIQUE (team_id, user_id, joined_at),
    CONSTRAINT ck_team_membership_status CHECK (status IN ('ACTIVE','ENDED')),
    CONSTRAINT ck_team_membership_dates CHECK (left_at IS NULL OR left_at >= joined_at)
);

CREATE TABLE user_role_grants (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    granted_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uq_user_role_grant UNIQUE (user_id, role_id, granted_at),
    CONSTRAINT ck_user_role_grant_dates CHECK (revoked_at IS NULL OR revoked_at >= granted_at)
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE offering_categories (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_offering_categories_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_offering_categories_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE offerings (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    category_id UUID NOT NULL REFERENCES offering_categories(id) ON DELETE RESTRICT,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    kind VARCHAR(20) NOT NULL,
    unit VARCHAR(80),
    status VARCHAR(20) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_offerings_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_offerings_kind CHECK (kind IN ('PRODUCT','SERVICE')),
    CONSTRAINT ck_offerings_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE'))
);

CREATE TABLE offering_attribute_definitions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    category_id UUID NOT NULL REFERENCES offering_categories(id) ON DELETE RESTRICT,
    key VARCHAR(100) NOT NULL,
    label VARCHAR(160) NOT NULL,
    value_type VARCHAR(30) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    validation_rule TEXT,
    options JSONB,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_attribute_def_category_key UNIQUE (category_id, key),
    CONSTRAINT ck_attribute_def_type CHECK (value_type IN ('TEXT','NUMBER','BOOLEAN','OPTION','DATE')),
    CONSTRAINT ck_attribute_def_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE offering_attribute_values (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    offering_id UUID NOT NULL REFERENCES offerings(id) ON DELETE RESTRICT,
    attribute_definition_id UUID NOT NULL REFERENCES offering_attribute_definitions(id) ON DELETE RESTRICT,
    value_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_offering_attribute_value UNIQUE (offering_id, attribute_definition_id)
);

CREATE TABLE offering_prices (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    offering_id UUID NOT NULL REFERENCES offerings(id) ON DELETE RESTRICT,
    amount NUMERIC(18,2) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    pricing_unit VARCHAR(80),
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_offering_prices_amount CHECK (amount >= 0),
    CONSTRAINT ck_offering_prices_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE sales_assignments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    offering_id UUID NOT NULL REFERENCES offerings(id) ON DELETE RESTRICT,
    sales_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assigned_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoke_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_sales_assignment_status CHECK (status IN ('ACTIVE','EXPIRED','REVOKED')),
    CONSTRAINT ck_sales_assignment_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_sales_assignment_revoked CHECK (status <> 'REVOKED' OR revoked_at IS NOT NULL)
);

CREATE TABLE source_systems (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_source_systems_org_code UNIQUE (organization_id, code),
    CONSTRAINT ck_source_systems_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE customers (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    display_name VARCHAR(180) NOT NULL,
    email VARCHAR(254),
    normalized_email VARCHAR(254),
    phone VARCHAR(40),
    normalized_phone VARCHAR(40),
    status VARCHAR(30) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_customers_identity CHECK (normalized_email IS NOT NULL OR normalized_phone IS NOT NULL)
);
CREATE UNIQUE INDEX uq_customers_org_email ON customers(organization_id, normalized_email) WHERE normalized_email IS NOT NULL;
CREATE UNIQUE INDEX uq_customers_org_phone ON customers(organization_id, normalized_phone) WHERE normalized_phone IS NOT NULL;

CREATE TABLE leads (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    offering_id UUID NOT NULL REFERENCES offerings(id) ON DELETE RESTRICT,
    display_name VARCHAR(180) NOT NULL,
    email VARCHAR(254),
    normalized_email VARCHAR(254),
    phone VARCHAR(40),
    normalized_phone VARCHAR(40),
    status VARCHAR(30) NOT NULL,
    qualified_at TIMESTAMPTZ,
    converted_customer_id UUID REFERENCES customers(id) ON DELETE RESTRICT,
    converted_at TIMESTAMPTZ,
    disqualification_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_leads_identity CHECK (normalized_email IS NOT NULL OR normalized_phone IS NOT NULL),
    CONSTRAINT ck_leads_status CHECK (status IN ('NEW','QUALIFIED','CONVERTED','DISQUALIFIED')),
    CONSTRAINT ck_leads_conversion CHECK (
      (status='CONVERTED' AND converted_customer_id IS NOT NULL AND converted_at IS NOT NULL)
      OR (status<>'CONVERTED' AND converted_customer_id IS NULL AND converted_at IS NULL)
    ),
    CONSTRAINT ck_leads_disqualified CHECK (status <> 'DISQUALIFIED' OR disqualification_reason IS NOT NULL)
);

CREATE TABLE source_record_links (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    source_system_id UUID NOT NULL REFERENCES source_systems(id) ON DELETE RESTRICT,
    subject_type VARCHAR(20) NOT NULL,
    subject_id UUID NOT NULL,
    external_id VARCHAR(180) NOT NULL,
    source_entity_type VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    linked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_source_record_external UNIQUE (organization_id, source_system_id, source_entity_type, external_id),
    CONSTRAINT ck_source_record_subject CHECK (subject_type IN ('CUSTOMER','LEAD')),
    CONSTRAINT ck_source_record_status CHECK (status IN ('ACTIVE','ARCHIVED'))
);

CREATE TABLE duplicate_review_cases (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    subject_type VARCHAR(20) NOT NULL,
    subject_id UUID NOT NULL,
    candidate_type VARCHAR(20) NOT NULL,
    candidate_id UUID NOT NULL,
    match_reason TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_duplicate_subject CHECK (subject_type IN ('CUSTOMER','LEAD') AND candidate_type IN ('CUSTOMER','LEAD')),
    CONSTRAINT ck_duplicate_status CHECK (status IN ('OPEN','CONFIRMED_MATCH','REJECTED'))
);

CREATE TABLE opportunities (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    customer_id UUID REFERENCES customers(id) ON DELETE RESTRICT,
    lead_id UUID REFERENCES leads(id) ON DELETE RESTRICT,
    offering_id UUID NOT NULL REFERENCES offerings(id) ON DELETE RESTRICT,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title VARCHAR(220) NOT NULL,
    stage VARCHAR(30) NOT NULL,
    expected_value NUMERIC(18,2),
    currency_code CHAR(3) NOT NULL DEFAULT 'VND',
    expected_close_date DATE,
    closing_reason TEXT,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_opportunity_subject_xor CHECK ((customer_id IS NOT NULL) <> (lead_id IS NOT NULL)),
    CONSTRAINT ck_opportunity_stage CHECK (stage IN ('NEW','QUALIFIED','PROPOSAL','WON','LOST')),
    CONSTRAINT ck_opportunity_value CHECK (expected_value IS NULL OR expected_value >= 0),
    CONSTRAINT ck_opportunity_closed CHECK (
      (stage IN ('WON','LOST') AND closing_reason IS NOT NULL AND closed_at IS NOT NULL)
      OR (stage NOT IN ('WON','LOST') AND closed_at IS NULL)
    )
);

CREATE TABLE handover_requests (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    handover_type VARCHAR(20) NOT NULL,
    requested_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    target_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    reviewed_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_handover_type CHECK (handover_type IN ('SALES','CARE')),
    CONSTRAINT ck_handover_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_handover_review CHECK (
      (status='PENDING' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL)
      OR (status='APPROVED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND target_user_id IS NOT NULL)
      OR (status='REJECTED' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND rejection_reason IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uq_handover_pending_customer ON handover_requests(customer_id) WHERE status='PENDING';

CREATE TABLE customer_assignments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    assignment_type VARCHAR(20) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL,
    assigned_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    source VARCHAR(30) NOT NULL,
    handover_request_id UUID REFERENCES handover_requests(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_customer_assignment_type CHECK (assignment_type IN ('SALES','CARE')),
    CONSTRAINT ck_customer_assignment_status CHECK (status IN ('ACTIVE','ENDED')),
    CONSTRAINT ck_customer_assignment_source CHECK (source IN ('INITIAL','MANUAL','HANDOVER','IMPORT')),
    CONSTRAINT ck_customer_assignment_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);
CREATE UNIQUE INDEX uq_customer_active_assignment_type ON customer_assignments(customer_id, assignment_type) WHERE status='ACTIVE';

CREATE TABLE interactions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    customer_id UUID REFERENCES customers(id) ON DELETE RESTRICT,
    lead_id UUID REFERENCES leads(id) ON DELETE RESTRICT,
    opportunity_id UUID REFERENCES opportunities(id) ON DELETE RESTRICT,
    recorded_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    subject VARCHAR(220) NOT NULL,
    content TEXT NOT NULL,
    outcome TEXT,
    visibility VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_interaction_subject_xor CHECK ((customer_id IS NOT NULL) <> (lead_id IS NOT NULL)),
    CONSTRAINT ck_interaction_visibility CHECK (visibility IN ('CARE_VISIBLE','SALES_ONLY'))
);

CREATE TABLE feedbacks (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    customer_id UUID REFERENCES customers(id) ON DELETE RESTRICT,
    lead_id UUID REFERENCES leads(id) ON DELETE RESTRICT,
    recorded_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    category VARCHAR(60) NOT NULL,
    content TEXT NOT NULL,
    rating INTEGER,
    visibility VARCHAR(20) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_feedback_subject_xor CHECK ((customer_id IS NOT NULL) <> (lead_id IS NOT NULL)),
    CONSTRAINT ck_feedback_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT ck_feedback_visibility CHECK (visibility IN ('CARE_VISIBLE','SALES_ONLY'))
);

CREATE TABLE crm_tasks (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    customer_id UUID REFERENCES customers(id) ON DELETE RESTRICT,
    lead_id UUID REFERENCES leads(id) ON DELETE RESTRICT,
    opportunity_id UUID REFERENCES opportunities(id) ON DELETE RESTRICT,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title VARCHAR(220) NOT NULL,
    description TEXT,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    due_at TIMESTAMPTZ NOT NULL,
    result TEXT,
    origin VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(160),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_task_subject_xor CHECK ((customer_id IS NOT NULL) <> (lead_id IS NOT NULL)),
    CONSTRAINT ck_task_status CHECK (status IN ('OPEN','IN_PROGRESS','DONE','CANCELLED')),
    CONSTRAINT ck_task_origin CHECK (origin IN ('MANUAL','WORKFLOW','IMPORT')),
    CONSTRAINT ck_task_complete CHECK ((status='DONE' AND completed_at IS NOT NULL) OR status<>'DONE')
);
CREATE UNIQUE INDEX uq_task_idempotency ON crm_tasks(organization_id, idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE import_jobs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    source_system_id UUID NOT NULL REFERENCES source_systems(id) ON DELETE RESTRICT,
    submitted_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    approved_by_user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL,
    original_file_ref TEXT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_checksum VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    total_rows INTEGER NOT NULL DEFAULT 0,
    valid_rows INTEGER NOT NULL DEFAULT 0,
    error_rows INTEGER NOT NULL DEFAULT 0,
    submitted_at TIMESTAMPTZ NOT NULL,
    validated_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    committed_at TIMESTAMPTZ,
    failure_reason TEXT,
    CONSTRAINT uq_import_job_key UNIQUE (organization_id, idempotency_key),
    CONSTRAINT ck_import_job_status CHECK (status IN ('RECEIVED','VALIDATING','REVIEW_REQUIRED','COMMITTED','FAILED')),
    CONSTRAINT ck_import_job_counts CHECK (retry_count>=0 AND total_rows>=0 AND valid_rows>=0 AND error_rows>=0),
    CONSTRAINT ck_import_job_approval CHECK (
      (status='COMMITTED' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL AND committed_at IS NOT NULL)
      OR status<>'COMMITTED'
    )
);

CREATE TABLE import_rows (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    import_job_id UUID NOT NULL REFERENCES import_jobs(id) ON DELETE RESTRICT,
    row_number INTEGER NOT NULL,
    raw_payload JSONB NOT NULL,
    normalized_payload JSONB,
    status VARCHAR(20) NOT NULL,
    error_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    duplicate_candidate BOOLEAN NOT NULL DEFAULT FALSE,
    committed_entity_type VARCHAR(20),
    committed_entity_id UUID,
    row_fingerprint VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_import_row_number UNIQUE (import_job_id, row_number),
    CONSTRAINT ck_import_row_status CHECK (status IN ('VALID','INVALID','DUPLICATE','SKIPPED')),
    CONSTRAINT ck_import_row_committed_pair CHECK ((committed_entity_type IS NULL) = (committed_entity_id IS NULL)),
    CONSTRAINT ck_import_row_entity_type CHECK (committed_entity_type IS NULL OR committed_entity_type IN ('CUSTOMER','LEAD'))
);

CREATE TABLE import_decisions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    import_job_id UUID NOT NULL UNIQUE REFERENCES import_jobs(id) ON DELETE RESTRICT,
    decision VARCHAR(20) NOT NULL,
    decided_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    decided_at TIMESTAMPTZ NOT NULL,
    reason TEXT,
    CONSTRAINT ck_import_decision CHECK (decision IN ('APPROVED','REJECTED'))
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    actor_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    action VARCHAR(120) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    before_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    after_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    safe_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    result VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload JSONB NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL,
    delivery_status VARCHAR(20) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT uq_outbox_idempotency UNIQUE (organization_id, idempotency_key),
    CONSTRAINT ck_outbox_status CHECK (delivery_status IN ('PENDING','PUBLISHED','FAILED')),
    CONSTRAINT ck_outbox_retry CHECK (retry_count >= 0)
);

CREATE TABLE workflow_executions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    outbox_event_id UUID REFERENCES outbox_events(id) ON DELETE RESTRICT,
    workflow_type VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_task_id UUID REFERENCES crm_tasks(id) ON DELETE RESTRICT,
    CONSTRAINT uq_workflow_idempotency UNIQUE (organization_id, idempotency_key),
    CONSTRAINT ck_workflow_status CHECK (status IN ('PENDING','RUNNING','DONE','FAILED')),
    CONSTRAINT ck_workflow_retry CHECK (retry_count >= 0)
);

CREATE TABLE support_requests (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    assigned_to_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title VARCHAR(220) NOT NULL,
    category VARCHAR(80) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    escalation_reason TEXT,
    resolution TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    CONSTRAINT ck_support_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    CONSTRAINT ck_support_status CHECK (status IN ('OPEN','IN_PROGRESS','ESCALATED','RESOLVED','CLOSED')),
    CONSTRAINT ck_support_escalated CHECK (status <> 'ESCALATED' OR escalation_reason IS NOT NULL),
    CONSTRAINT ck_support_resolution CHECK (status NOT IN ('RESOLVED','CLOSED') OR resolution IS NOT NULL)
);

CREATE TABLE support_responses (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    support_request_id UUID NOT NULL REFERENCES support_requests(id) ON DELETE RESTRICT,
    content TEXT NOT NULL,
    status_after VARCHAR(30),
    created_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_support_response_status CHECK (status_after IS NULL OR status_after IN ('OPEN','IN_PROGRESS','ESCALATED','RESOLVED','CLOSED'))
);

CREATE TABLE support_attachments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    support_request_id UUID NOT NULL REFERENCES support_requests(id) ON DELETE RESTRICT,
    support_response_id UUID REFERENCES support_responses(id) ON DELETE RESTRICT,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_ref TEXT NOT NULL,
    uploaded_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    uploaded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_support_attachment_size CHECK (file_size > 0)
);

-- Indexes used by scope filters and CRM screens.
CREATE INDEX ix_users_org_status ON users(organization_id, status);
CREATE INDEX ix_team_memberships_user_active ON team_memberships(user_id, status);
CREATE INDEX ix_sales_assignments_user_status ON sales_assignments(sales_user_id, status, effective_from, effective_to);
CREATE INDEX ix_customers_org_owner ON customers(organization_id, owner_user_id);
CREATE INDEX ix_leads_org_owner_status ON leads(organization_id, owner_user_id, status);
CREATE INDEX ix_opportunities_org_owner_stage ON opportunities(organization_id, owner_user_id, stage);
CREATE INDEX ix_interactions_customer_time ON interactions(customer_id, occurred_at DESC);
CREATE INDEX ix_interactions_lead_time ON interactions(lead_id, occurred_at DESC);
CREATE INDEX ix_tasks_owner_status_due ON crm_tasks(owner_user_id, status, due_at);
CREATE INDEX ix_import_jobs_org_status ON import_jobs(organization_id, status);
CREATE INDEX ix_support_assignee_status ON support_requests(assigned_to_user_id, status);
CREATE INDEX ix_customer_assignments_scope ON customer_assignments(customer_id, user_id, assignment_type, effective_from, effective_to);
CREATE INDEX ix_audit_entity_time ON audit_logs(organization_id, entity_type, entity_id, occurred_at DESC);

COMMENT ON TABLE customer_assignments IS 'Physical persistence for Customer SALES/CARE assignment history and authorization timeline. It supports Handover/Customer Care scope; it is not a one-class-one-table inference from the Domain Class Diagram.';
COMMENT ON TABLE source_record_links IS 'Polymorphic physical mapping for SourceRecordLink. subject_id is validated by backend/seed validator according to subject_type.';
COMMENT ON TABLE duplicate_review_cases IS 'Polymorphic suspected-duplicate review. subject/candidate references are validated by backend/seed validator.';

CREATE VIEW customer_360_summary AS
SELECT
    c.id AS customer_id,
    c.organization_id,
    c.owner_user_id,
    care.user_id AS care_user_id,
    c.display_name,
    c.normalized_email,
    c.normalized_phone,
    (SELECT COUNT(*) FROM source_record_links s WHERE s.subject_type='CUSTOMER' AND s.subject_id=c.id AND s.status='ACTIVE') AS source_count,
    (SELECT COUNT(*) FROM opportunities o WHERE o.customer_id=c.id) AS opportunity_count,
    (SELECT COUNT(*) FROM interactions i WHERE i.customer_id=c.id) AS interaction_count,
    (SELECT COUNT(*) FROM feedbacks f WHERE f.customer_id=c.id) AS feedback_count,
    (SELECT COUNT(*) FROM crm_tasks t WHERE t.customer_id=c.id AND t.status IN ('OPEN','IN_PROGRESS')) AS open_task_count,
    (SELECT COUNT(*) FROM support_requests sr WHERE sr.customer_id=c.id AND sr.status NOT IN ('RESOLVED','CLOSED')) AS open_support_count,
    (SELECT MAX(i.occurred_at) FROM interactions i WHERE i.customer_id=c.id) AS last_interaction_at,
    CURRENT_TIMESTAMP AS refreshed_at
FROM customers c
LEFT JOIN LATERAL (
    SELECT ca.user_id
    FROM customer_assignments ca
    WHERE ca.customer_id=c.id AND ca.assignment_type='CARE' AND ca.status='ACTIVE'
    ORDER BY ca.effective_from DESC
    LIMIT 1
) care ON TRUE;

COMMIT;
