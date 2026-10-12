-- Align the six reviewed demo tenants with Nhi's published login codes.
-- Fixed IDs keep this migration compatible with PostgreSQL and the H2 fixture.
-- Preserve explicit codes and the generated code of any other organization.
UPDATE organizations SET code = 'edu'
WHERE id = '0b47be39-3b3c-5195-a4e2-8a29ffbadcf0' AND code LIKE 'org-%';
UPDATE organizations SET code = 'rea'
WHERE id = '47dcbd1f-c1c3-5130-8596-58d76fd2676e' AND code LIKE 'org-%';
UPDATE organizations SET code = 'tech'
WHERE id = '001ba809-bda6-52d8-8645-5e4ba33533b4' AND code LIKE 'org-%';
UPDATE organizations SET code = 'ret'
WHERE id = '83236f5d-2b8b-52bb-af64-ccde174487ab' AND code LIKE 'org-%';
UPDATE organizations SET code = 'log'
WHERE id = 'a477cbf4-afb4-5960-b165-41537f66ac13' AND code LIKE 'org-%';
UPDATE organizations SET code = 'cli'
WHERE id = '68809a13-d597-5c34-a5d8-0d18d9c6cc7a' AND code LIKE 'org-%';
