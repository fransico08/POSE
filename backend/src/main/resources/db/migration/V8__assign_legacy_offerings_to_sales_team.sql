-- Backfill legacy Offering ownership only where it is unambiguous: the organization has an ACTIVE team
-- with code SALES (unique per organization). Rows that already have a team, or tenants without such a
-- team, are left for an Admin to assign through the application.
UPDATE public.offerings o
SET team_id = (
    SELECT t.id FROM public.teams t
    WHERE t.organization_id = o.organization_id AND t.code = 'SALES' AND t.status = 'ACTIVE'
)
WHERE o.team_id IS NULL
  AND EXISTS (
      SELECT 1 FROM public.teams t
      WHERE t.organization_id = o.organization_id AND t.code = 'SALES' AND t.status = 'ACTIVE'
  );
