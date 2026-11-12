-- W13 AI-02: durable channel inbox foundation. Secrets are never stored in this schema;
-- secret_ref names an environment variable resolved by the application at runtime.

ALTER TABLE public.interactions
    ADD CONSTRAINT uq_interactions_organization_id_id UNIQUE (organization_id, id);

CREATE TABLE public.channel_accounts (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES public.organizations(id),
    channel_type VARCHAR(30) NOT NULL CHECK (channel_type IN ('TELEGRAM','MESSENGER','ZALO_SIM')),
    display_name VARCHAR(160) NOT NULL,
    external_account_id VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    secret_ref VARCHAR(180) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_channel_accounts_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_channel_accounts_external UNIQUE (organization_id, channel_type, external_account_id)
);

CREATE TABLE public.conversations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES public.organizations(id),
    channel_account_id UUID NOT NULL,
    external_thread_id VARCHAR(180) NOT NULL,
    lead_id UUID,
    customer_id UUID,
    assigned_user_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','PENDING','CLOSED')),
    consent_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    last_inbound_at TIMESTAMP WITH TIME ZONE,
    last_message_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_conversations_org_id UNIQUE (organization_id, id),
    CONSTRAINT uq_conversations_external_thread UNIQUE (channel_account_id, external_thread_id),
    CONSTRAINT ck_conversations_single_profile CHECK (lead_id IS NULL OR customer_id IS NULL),
    CONSTRAINT fk_conversations_channel_org FOREIGN KEY (organization_id, channel_account_id)
        REFERENCES public.channel_accounts (organization_id, id),
    CONSTRAINT fk_conversations_lead_org FOREIGN KEY (organization_id, lead_id)
        REFERENCES public.leads (organization_id, id),
    CONSTRAINT fk_conversations_customer_org FOREIGN KEY (organization_id, customer_id)
        REFERENCES public.customers (organization_id, id),
    CONSTRAINT fk_conversations_assignee_org FOREIGN KEY (organization_id, assigned_user_id)
        REFERENCES public.users (organization_id, id)
);

CREATE TABLE public.conversation_messages (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES public.organizations(id),
    conversation_id UUID NOT NULL,
    direction VARCHAR(20) NOT NULL CHECK (direction IN ('INBOUND','OUTBOUND')),
    external_message_id VARCHAR(180) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    content TEXT NOT NULL,
    sent_by_user_id UUID,
    delivery_status VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    interaction_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_conversation_messages_external UNIQUE (conversation_id, direction, external_message_id),
    CONSTRAINT fk_conversation_messages_conversation_org FOREIGN KEY (organization_id, conversation_id)
        REFERENCES public.conversations (organization_id, id),
    CONSTRAINT fk_conversation_messages_sender_org FOREIGN KEY (organization_id, sent_by_user_id)
        REFERENCES public.users (organization_id, id),
    CONSTRAINT fk_conversation_messages_interaction_org FOREIGN KEY (organization_id, interaction_id)
        REFERENCES public.interactions (organization_id, id)
);

CREATE INDEX ix_conversations_org_updated ON public.conversations (organization_id, last_message_at DESC, id);
CREATE INDEX ix_conversations_org_assignee_status ON public.conversations (organization_id, assigned_user_id, status, last_message_at DESC);
CREATE INDEX ix_conversation_messages_org_conversation_time ON public.conversation_messages (organization_id, conversation_id, occurred_at, id);

COMMENT ON TABLE public.channel_accounts IS 'Tenant channel configuration. secret_ref stores an environment-variable name, never its value.';
COMMENT ON COLUMN public.conversations.assigned_user_id IS 'May be null for a received conversation awaiting the approved assignment rule.';

INSERT INTO public.permissions(id,code,description,created_at)
SELECT '27fdd389-32da-46d9-94e5-120000000005','channel.manage','Manage organization channel accounts.',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.permissions WHERE code='channel.manage');
INSERT INTO public.permissions(id,code,description,created_at)
SELECT '27fdd389-32da-46d9-94e5-120000000006','conversation.read','Read conversations within the actor profile scope.',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.permissions WHERE code='conversation.read');
INSERT INTO public.permissions(id,code,description,created_at)
SELECT '27fdd389-32da-46d9-94e5-120000000007','conversation.reply','Reply to conversations within the actor profile scope.',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.permissions WHERE code='conversation.reply');

INSERT INTO public.role_permissions(role_id,permission_id,granted_at)
SELECT r.id,p.id,CURRENT_TIMESTAMP
FROM public.roles r CROSS JOIN public.permissions p
WHERE r.status='ACTIVE' AND (
    (p.code='channel.manage' AND r.code='ADMIN') OR
    (p.code='conversation.read' AND r.code IN ('ADMIN','MANAGER','SALES_REP','CUSTOMER_CARE')) OR
    (p.code='conversation.reply' AND r.code IN ('MANAGER','SALES_REP','CUSTOMER_CARE'))
)
AND NOT EXISTS (
    SELECT 1 FROM public.role_permissions existing
    WHERE existing.role_id=r.id AND existing.permission_id=p.id
);
