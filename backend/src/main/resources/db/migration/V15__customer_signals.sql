-- V14 belongs to channel ownership; do not apply V15 to long-lived databases before V14.
ALTER TABLE conversation_messages ADD CONSTRAINT uq_conversation_messages_org_id UNIQUE (organization_id,id);

CREATE TABLE customer_signal_extractions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    message_id UUID NOT NULL,
    invocation_id UUID,
    status VARCHAR(30) NOT NULL CHECK (status IN ('SUCCEEDED','FAILED')),
    error_code VARCHAR(40),
    success_message_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_signal_extraction_success CHECK ((status='SUCCEEDED' AND success_message_id IS NOT NULL AND success_message_id=message_id) OR (status<>'SUCCEEDED' AND success_message_id IS NULL)),
    CONSTRAINT uq_signal_extraction_success UNIQUE (organization_id,success_message_id),
    CONSTRAINT fk_signal_extraction_message FOREIGN KEY (organization_id,message_id) REFERENCES conversation_messages(organization_id,id),
    CONSTRAINT fk_signal_extraction_invocation FOREIGN KEY (organization_id,invocation_id) REFERENCES ai_invocations(organization_id,id)
);
CREATE INDEX idx_signal_extractions_message ON customer_signal_extractions(organization_id,message_id,status,error_code);

CREATE TABLE customer_signals (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    lead_id UUID,
    customer_id UUID,
    message_id UUID NOT NULL,
    signal_type VARCHAR(30) NOT NULL CHECK (signal_type IN ('INTENT','SENTIMENT','URGENCY','OBJECTION','OFFERING_INTEREST')),
    "value" VARCHAR(80) NOT NULL,
    confidence NUMERIC(5,4) CHECK (confidence >= 0 AND confidence <= 1),
    evidence_text TEXT,
    source VARCHAR(10) NOT NULL CHECK (source IN ('AI','HUMAN')),
    ai_message_id UUID,
    invocation_id UUID,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROPOSED','ACCEPTED','EDITED','REJECTED')),
    model_version VARCHAR(120),
    reviewed_by_user_id UUID,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_customer_signals_subject CHECK ((lead_id IS NOT NULL AND customer_id IS NULL) OR (lead_id IS NULL AND customer_id IS NOT NULL)),
    CONSTRAINT ck_customer_signals_ai_key CHECK ((source='AI' AND ai_message_id IS NOT NULL AND ai_message_id=message_id) OR (source='HUMAN' AND ai_message_id IS NULL)),
    CONSTRAINT ck_customer_signals_ai_invocation CHECK (source<>'AI' OR invocation_id IS NOT NULL),
    CONSTRAINT uq_customer_signals_ai UNIQUE (organization_id,ai_message_id,signal_type),
    CONSTRAINT fk_customer_signals_lead FOREIGN KEY (organization_id,lead_id) REFERENCES leads(organization_id,id),
    CONSTRAINT fk_customer_signals_customer FOREIGN KEY (organization_id,customer_id) REFERENCES customers(organization_id,id),
    CONSTRAINT fk_customer_signals_message FOREIGN KEY (organization_id,message_id) REFERENCES conversation_messages(organization_id,id),
    CONSTRAINT fk_customer_signals_invocation FOREIGN KEY (organization_id,invocation_id) REFERENCES ai_invocations(organization_id,id),
    CONSTRAINT fk_customer_signals_reviewer FOREIGN KEY (organization_id,reviewed_by_user_id) REFERENCES users(organization_id,id)
);
CREATE INDEX idx_customer_signals_customer ON customer_signals(organization_id,customer_id,created_at);
CREATE INDEX idx_customer_signals_lead ON customer_signals(organization_id,lead_id,created_at);
