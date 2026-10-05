-- Supplemental assertions for Tai's foundation, on an isolated restored copy of Nhi DB v2.
-- psql -v ON_ERROR_STOP=1 -f database/verify-foundation-v2.sql
-- Read-only assertions; a failure stops the command with a nonzero exit code.
DO $$
BEGIN
    IF EXISTS(SELECT 1 FROM users u LEFT JOIN credentials c ON c.user_id=u.id WHERE c.user_id IS NULL)
        THEN RAISE EXCEPTION 'User missing credential'; END IF;
    -- Escape dollar signs in the BCrypt regex; never output stored hashes.
    IF EXISTS(SELECT 1 FROM credentials WHERE password_hash !~ ('^' || chr(92)||chr(36)||'2[aby]'||chr(92)||chr(36)||'[0-9]{2}'||chr(92)||chr(36)||'[./A-Za-z0-9]{53}$'))
        THEN RAISE EXCEPTION 'Credential hash is not BCrypt'; END IF;
    IF EXISTS(SELECT 1 FROM users WHERE normalized_email<>lower(trim(email)))
        THEN RAISE EXCEPTION 'Email normalization mismatch'; END IF;
    IF EXISTS(SELECT 1 FROM users GROUP BY organization_id,lower(trim(email)) HAVING count(*)>1)
        THEN RAISE EXCEPTION 'Duplicate tenant email'; END IF;
    IF EXISTS(SELECT 1 FROM user_role_grants g JOIN users u ON u.id=g.user_id JOIN roles r ON r.id=g.role_id
              WHERE g.organization_id<>u.organization_id OR g.organization_id<>r.organization_id)
        THEN RAISE EXCEPTION 'Role grant crosses tenant'; END IF;
    IF EXISTS(SELECT 1 FROM team_memberships m JOIN users u ON u.id=m.user_id JOIN teams t ON t.id=m.team_id
              WHERE m.organization_id<>u.organization_id OR m.organization_id<>t.organization_id)
        THEN RAISE EXCEPTION 'Team membership crosses tenant'; END IF;
    IF EXISTS(SELECT 1 FROM teams t JOIN users u ON u.id=t.manager_user_id JOIN departments d ON d.id=t.department_id
              WHERE t.organization_id<>u.organization_id OR t.organization_id<>d.organization_id)
        THEN RAISE EXCEPTION 'Team manager/department crosses tenant'; END IF;
    IF EXISTS(SELECT 1 FROM teams t JOIN users u ON u.id=t.manager_user_id WHERE t.status='ACTIVE' AND
              (u.status<>'ACTIVE' OR u.archived_at IS NOT NULL OR NOT EXISTS(
               SELECT 1 FROM user_role_grants g JOIN roles r ON r.id=g.role_id WHERE g.user_id=u.id
                 AND g.organization_id=t.organization_id AND r.organization_id=t.organization_id AND r.code='MANAGER'
                 AND r.status='ACTIVE' AND g.granted_at<=CURRENT_TIMESTAMP AND (g.revoked_at IS NULL OR g.revoked_at>CURRENT_TIMESTAMP))))
        THEN RAISE EXCEPTION 'Active team has no effective Manager'; END IF;
    IF EXISTS(SELECT 1 FROM user_role_grants WHERE granted_at<=CURRENT_TIMESTAMP AND (revoked_at IS NULL OR revoked_at>CURRENT_TIMESTAMP)
              GROUP BY organization_id,user_id,role_id HAVING count(*)>1)
        THEN RAISE EXCEPTION 'Duplicate effective role grant'; END IF;
    IF EXISTS(SELECT 1 FROM team_memberships WHERE status='ACTIVE' AND joined_at<=CURRENT_TIMESTAMP AND (left_at IS NULL OR left_at>CURRENT_TIMESTAMP)
              GROUP BY organization_id,team_id,user_id HAVING count(*)>1)
        THEN RAISE EXCEPTION 'Duplicate effective membership'; END IF;
    IF EXISTS(SELECT 1 FROM role_permissions rp JOIN roles r ON r.id=rp.role_id JOIN permissions p ON p.id=rp.permission_id
              WHERE p.code='identity.role.manage' AND r.code<>'ADMIN')
        THEN RAISE EXCEPTION 'Non-admin role has identity.role.manage'; END IF;
END $$;
SELECT 'Foundation DB v2 assertions passed' AS result;
