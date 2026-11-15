-- DEC-23 D1/D2/D3: team ownership, conversation-only work records, and system actor metadata.
-- V14 must be applied before V15 on long-lived databases; do not enable out-of-order migrations.

ALTER TABLE public.channel_accounts ADD COLUMN team_id UUID;
ALTER TABLE public.channel_accounts ADD COLUMN default_assignee_user_id UUID;
ALTER TABLE public.channel_accounts ADD CONSTRAINT ck_channel_accounts_default_owner_team
    CHECK (default_assignee_user_id IS NULL OR team_id IS NOT NULL);
ALTER TABLE public.channel_accounts ADD CONSTRAINT fk_channel_accounts_team_org
    FOREIGN KEY (organization_id, team_id) REFERENCES public.teams (organization_id, id);
ALTER TABLE public.channel_accounts ADD CONSTRAINT fk_channel_accounts_default_assignee_org
    FOREIGN KEY (organization_id, default_assignee_user_id) REFERENCES public.users (organization_id, id);

-- W13 Tasks identify their conversation separately from a CRM Lead/Customer.
-- System-created Tasks have a null user ID plus an explicit SYSTEM actor type;
-- the assignee is never recorded as their creator.
ALTER TABLE public.crm_tasks DROP CONSTRAINT ck_task_subject_xor;
ALTER TABLE public.crm_tasks ALTER COLUMN created_by_user_id DROP NOT NULL;
ALTER TABLE public.crm_tasks ADD COLUMN conversation_id UUID;
ALTER TABLE public.crm_tasks ADD COLUMN created_by_actor_type VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE public.crm_tasks ADD CONSTRAINT ck_task_created_by_actor CHECK (
    (created_by_actor_type='USER' AND created_by_user_id IS NOT NULL) OR
    (created_by_actor_type='SYSTEM' AND created_by_user_id IS NULL)
);
ALTER TABLE public.crm_tasks ADD CONSTRAINT ck_task_subject_xor CHECK (
    (conversation_id IS NOT NULL AND customer_id IS NULL AND lead_id IS NULL) OR
    (conversation_id IS NULL AND ((customer_id IS NOT NULL) <> (lead_id IS NOT NULL)))
);
ALTER TABLE public.crm_tasks ADD CONSTRAINT fk_tasks_conversation_org FOREIGN KEY (organization_id, conversation_id)
    REFERENCES public.conversations (organization_id, id);
CREATE UNIQUE INDEX uq_workflow_first_response_conversation
    ON public.crm_tasks (organization_id, conversation_id);
CREATE INDEX ix_tasks_org_conversation_due
    ON public.crm_tasks (organization_id, conversation_id, due_at, id);

-- Conversation Interactions may exist before a profile is linked, then retain both links
-- after a human manually links the conversation to an existing Lead or Customer.
ALTER TABLE public.interactions DROP CONSTRAINT ck_interaction_subject_xor;
ALTER TABLE public.interactions ALTER COLUMN recorded_by_user_id DROP NOT NULL;
ALTER TABLE public.interactions ADD COLUMN conversation_id UUID;
ALTER TABLE public.interactions ADD COLUMN recorded_by_actor_type VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE public.interactions ADD CONSTRAINT ck_interaction_recorded_by_actor CHECK (
    (recorded_by_actor_type='USER' AND recorded_by_user_id IS NOT NULL) OR
    (recorded_by_actor_type='SYSTEM' AND recorded_by_user_id IS NULL)
);
ALTER TABLE public.interactions ADD CONSTRAINT ck_interaction_subject_xor CHECK (
    (conversation_id IS NOT NULL AND (customer_id IS NULL OR lead_id IS NULL)) OR
    (conversation_id IS NULL AND ((customer_id IS NOT NULL) <> (lead_id IS NOT NULL)))
);
ALTER TABLE public.interactions ADD CONSTRAINT fk_interactions_conversation_org FOREIGN KEY (organization_id, conversation_id)
    REFERENCES public.conversations (organization_id, id);
CREATE INDEX ix_interactions_org_conversation_time
    ON public.interactions (organization_id, conversation_id, occurred_at DESC, id);

CREATE INDEX ix_channel_accounts_org_team
    ON public.channel_accounts (organization_id, team_id, status);

INSERT INTO public.permissions(id,code,description,created_at)
SELECT '27fdd389-32da-46d9-94e5-120000000008','conversation.assign',
       'Assign conversations to active members of the channel team.',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.permissions WHERE code='conversation.assign');

INSERT INTO public.role_permissions(role_id,permission_id,granted_at)
SELECT r.id,p.id,CURRENT_TIMESTAMP
FROM public.roles r CROSS JOIN public.permissions p
WHERE r.status='ACTIVE' AND r.code='MANAGER' AND p.code='conversation.assign'
AND NOT EXISTS (
    SELECT 1 FROM public.role_permissions existing
    WHERE existing.role_id=r.id AND existing.permission_id=p.id
);
