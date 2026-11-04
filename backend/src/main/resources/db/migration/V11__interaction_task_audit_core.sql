-- Enforce tenant consistency for CRM work records and index the W06 query paths.
-- Existing rows are preserved; invalid legacy references cause Flyway to stop rather than rewrite them.

ALTER TABLE public.crm_tasks ADD COLUMN cancellation_reason TEXT;

ALTER TABLE public.users
    ADD CONSTRAINT uq_users_organization_id_id UNIQUE (organization_id, id);
ALTER TABLE public.customers
    ADD CONSTRAINT uq_customers_organization_id_id UNIQUE (organization_id, id);
ALTER TABLE public.leads
    ADD CONSTRAINT uq_leads_organization_id_id UNIQUE (organization_id, id);

ALTER TABLE public.interactions
    ADD CONSTRAINT fk_interactions_customer_org FOREIGN KEY (organization_id, customer_id)
        REFERENCES public.customers (organization_id, id);
ALTER TABLE public.interactions
    ADD CONSTRAINT fk_interactions_lead_org FOREIGN KEY (organization_id, lead_id)
        REFERENCES public.leads (organization_id, id);
ALTER TABLE public.interactions
    ADD CONSTRAINT fk_interactions_opportunity_org FOREIGN KEY (organization_id, opportunity_id)
        REFERENCES public.opportunities (organization_id, id);
ALTER TABLE public.interactions
    ADD CONSTRAINT fk_interactions_recorder_org FOREIGN KEY (organization_id, recorded_by_user_id)
        REFERENCES public.users (organization_id, id);

ALTER TABLE public.crm_tasks
    ADD CONSTRAINT fk_tasks_customer_org FOREIGN KEY (organization_id, customer_id)
        REFERENCES public.customers (organization_id, id);
ALTER TABLE public.crm_tasks
    ADD CONSTRAINT fk_tasks_lead_org FOREIGN KEY (organization_id, lead_id)
        REFERENCES public.leads (organization_id, id);
ALTER TABLE public.crm_tasks
    ADD CONSTRAINT fk_tasks_opportunity_org FOREIGN KEY (organization_id, opportunity_id)
        REFERENCES public.opportunities (organization_id, id);
ALTER TABLE public.crm_tasks
    ADD CONSTRAINT fk_tasks_owner_org FOREIGN KEY (organization_id, owner_user_id)
        REFERENCES public.users (organization_id, id);
ALTER TABLE public.crm_tasks
    ADD CONSTRAINT fk_tasks_creator_org FOREIGN KEY (organization_id, created_by_user_id)
        REFERENCES public.users (organization_id, id);

ALTER TABLE public.audit_logs
    ADD CONSTRAINT fk_audit_actor_org FOREIGN KEY (organization_id, actor_user_id)
        REFERENCES public.users (organization_id, id);

CREATE INDEX ix_interactions_org_occurred ON public.interactions (organization_id, occurred_at DESC, id);
CREATE INDEX ix_interactions_org_customer_time ON public.interactions (organization_id, customer_id, occurred_at DESC, id);
CREATE INDEX ix_interactions_org_lead_time ON public.interactions (organization_id, lead_id, occurred_at DESC, id);
CREATE INDEX ix_interactions_org_opportunity_time ON public.interactions (organization_id, opportunity_id, occurred_at DESC, id);
CREATE INDEX ix_tasks_org_owner_status_due ON public.crm_tasks (organization_id, owner_user_id, status, due_at, id);
CREATE INDEX ix_tasks_org_customer_due ON public.crm_tasks (organization_id, customer_id, due_at, id);
CREATE INDEX ix_tasks_org_lead_due ON public.crm_tasks (organization_id, lead_id, due_at, id);
CREATE INDEX ix_tasks_org_opportunity_due ON public.crm_tasks (organization_id, opportunity_id, due_at, id);
CREATE INDEX ix_audit_org_w06_entity_time ON public.audit_logs (organization_id, entity_type, entity_id, occurred_at DESC, id);
