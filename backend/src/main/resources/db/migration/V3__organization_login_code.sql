-- Add the tenant identifier agreed with Tai; existing organization IDs/names are preserved.
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS code VARCHAR(100);
UPDATE organizations SET code = 'org-' || REPLACE(CAST(id AS VARCHAR), '-', '') WHERE code IS NULL;
ALTER TABLE organizations ALTER COLUMN code SET NOT NULL;
ALTER TABLE organizations ADD CONSTRAINT ck_organization_login_code CHECK (code = LOWER(TRIM(code)) AND LENGTH(code) > 0);
CREATE UNIQUE INDEX uq_organization_login_code ON organizations(code);
