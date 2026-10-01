-- Target identity fixture for tests only, not Nhi's production baseline or demo seed.
CREATE TABLE organizations (
    id UUID PRIMARY KEY, code VARCHAR(100) NOT NULL, name VARCHAR(150) NOT NULL,
    industry VARCHAR(120), active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_organizations_code ON organizations(code);
CREATE TABLE users (
    id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id),
    full_name VARCHAR(150) NOT NULL, email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX uq_users_org_email ON users(organization_id, email);
CREATE TABLE roles (
    organization_id UUID NOT NULL REFERENCES organizations(id), code VARCHAR(40) NOT NULL,
    PRIMARY KEY (organization_id, code)
);
CREATE TABLE permissions (code VARCHAR(100) PRIMARY KEY);
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id), role VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, role)
);
CREATE TABLE role_permissions (
    organization_id UUID NOT NULL, role_code VARCHAR(40) NOT NULL,
    permission_code VARCHAR(100) NOT NULL REFERENCES permissions(code),
    PRIMARY KEY (organization_id, role_code, permission_code),
    FOREIGN KEY (organization_id, role_code) REFERENCES roles(organization_id, code)
);
CREATE TABLE teams (
    id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id),
    code VARCHAR(100) NOT NULL, name VARCHAR(150) NOT NULL,
    manager_user_id UUID REFERENCES users(id), active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE team_memberships (
    id UUID PRIMARY KEY, team_id UUID NOT NULL REFERENCES teams(id),
    user_id UUID NOT NULL REFERENCES users(id),
    joined_at DATE NOT NULL, ended_at DATE, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY, actor_user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(100) NOT NULL, entity_type VARCHAR(100) NOT NULL, entity_id UUID NOT NULL,
    old_value JSONB, new_value JSONB, occurred_at TIMESTAMP NOT NULL
);
