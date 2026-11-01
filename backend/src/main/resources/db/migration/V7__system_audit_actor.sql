ALTER TABLE public.audit_logs
    ALTER COLUMN actor_user_id DROP NOT NULL;

ALTER TABLE public.audit_logs
    ADD COLUMN actor_type character varying(20) NOT NULL DEFAULT 'USER';

ALTER TABLE public.audit_logs
    ADD COLUMN actor_system_key character varying(100);

ALTER TABLE public.audit_logs
    ADD CONSTRAINT ck_audit_actor_identity CHECK (
        (actor_type='USER' AND actor_user_id IS NOT NULL AND actor_system_key IS NULL)
        OR
        (actor_type='SYSTEM' AND actor_user_id IS NULL AND actor_system_key IS NOT NULL
            AND length(trim(actor_system_key)) > 0)
    );
