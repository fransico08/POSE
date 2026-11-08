CREATE TABLE ai_invocations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    actor_user_id UUID NOT NULL REFERENCES users(id),
    use_case VARCHAR(40) NOT NULL CHECK (use_case IN ('C360_SUMMARY','HANDOVER_BRIEF','REPLY_DRAFT','SIGNAL_EXTRACT')),
    subject_type VARCHAR(20) NOT NULL CHECK (subject_type IN ('CUSTOMER','LEAD')),
    subject_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL,
    model VARCHAR(120), prompt_version VARCHAR(40), input_hash VARCHAR(64),
    redaction_count INTEGER NOT NULL DEFAULT 0,
    input_tokens BIGINT, output_tokens BIGINT, cost_estimate NUMERIC(18,8), latency_ms BIGINT,
    status VARCHAR(30) NOT NULL CHECK (status IN ('SUCCEEDED','FAILED','BLOCKED_BUDGET','DISABLED')),
    error_code VARCHAR(40), created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (organization_id,id)
);
CREATE INDEX idx_ai_invocations_org_created ON ai_invocations(organization_id,created_at);

CREATE TABLE ai_suggestions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id), invocation_id UUID NOT NULL,
    kind VARCHAR(40) NOT NULL, subject_type VARCHAR(20) NOT NULL, subject_id UUID NOT NULL,
    content TEXT NOT NULL, evidence JSONB, confidence NUMERIC(5,4),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROPOSED','ACCEPTED','EDITED','REJECTED','EXPIRED')),
    final_content TEXT, reviewed_by UUID REFERENCES users(id), reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (organization_id,invocation_id) REFERENCES ai_invocations(organization_id,id)
);
CREATE INDEX idx_ai_suggestions_org_subject ON ai_suggestions(organization_id,subject_type,subject_id,created_at);

INSERT INTO permissions(id,code,description,created_at)
SELECT '27fdd389-32da-46d9-94e5-120000000001','ai.assist.use','Use AI assistance within subject scope.',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='ai.assist.use');
INSERT INTO permissions(id,code,description,created_at)
SELECT '27fdd389-32da-46d9-94e5-120000000002','ai.signal.review','Review AI signals within subject scope.',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='ai.signal.review');
INSERT INTO permissions(id,code,description,created_at)
SELECT '27fdd389-32da-46d9-94e5-120000000003','ai.admin','Administer AI configuration.',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='ai.admin');
INSERT INTO permissions(id,code,description,created_at)
SELECT '27fdd389-32da-46d9-94e5-120000000004','ai.metrics.read','Read aggregate AI metrics.',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE code='ai.metrics.read');

INSERT INTO role_permissions(role_id,permission_id,granted_at)
SELECT r.id,p.id,CURRENT_TIMESTAMP FROM roles r CROSS JOIN permissions p
WHERE r.status='ACTIVE' AND (
    (p.code='ai.assist.use' AND r.code IN ('ADMIN','MANAGER','SALES_REP','CUSTOMER_CARE')) OR
    (p.code='ai.signal.review' AND r.code IN ('MANAGER','SALES_REP','CUSTOMER_CARE')) OR
    (p.code='ai.admin' AND r.code='ADMIN') OR
    (p.code='ai.metrics.read' AND r.code IN ('ADMIN','DATA_STAFF')))
AND NOT EXISTS (SELECT 1 FROM role_permissions x WHERE x.role_id=r.id AND x.permission_id=p.id);
