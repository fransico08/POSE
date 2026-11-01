-- Add team ownership without guessing the owner of existing Offering rows.
-- Legacy rows remain queryable by an authorized organization Admin until assigned.
ALTER TABLE public.teams
    ADD CONSTRAINT uq_teams_organization_id_id UNIQUE (organization_id, id);

ALTER TABLE public.offerings
    ADD COLUMN team_id uuid;

ALTER TABLE public.offerings
    ADD CONSTRAINT offerings_team_scope_fkey
    FOREIGN KEY (organization_id, team_id)
    REFERENCES public.teams (organization_id, id)
    ON DELETE RESTRICT;

CREATE INDEX ix_offerings_org_team_status_name
    ON public.offerings (organization_id, team_id, status, name, id);

COMMENT ON COLUMN public.offerings.team_id IS
    'Owning team. NULL is retained only for legacy Offering rows with no authoritative team mapping; new application writes require a Team.';

INSERT INTO public.permissions (id, code, description, created_at)
SELECT 'd2193c90-06cc-4a3c-b90d-b03912921ebc', 'offering.read',
       'Read active Offerings assigned to the current Sales user.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.permissions WHERE code='offering.read');

INSERT INTO public.role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM public.roles r
CROSS JOIN public.permissions p
WHERE r.code = 'SALES_REP'
  AND r.status = 'ACTIVE'
  AND p.code = 'offering.read'
  AND NOT EXISTS (
      SELECT 1 FROM public.role_permissions existing
      WHERE existing.role_id=r.id AND existing.permission_id=p.id
  );
