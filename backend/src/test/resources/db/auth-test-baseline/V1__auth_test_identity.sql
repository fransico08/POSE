-- Identity subset of Nhi's DB v2 for H2 regression tests only.
CREATE TABLE organizations (
 id UUID PRIMARY KEY, name VARCHAR(160) NOT NULL, industry VARCHAR(120),
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', metadata JSONB DEFAULT '{}' NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE users (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id),
 full_name VARCHAR(160) NOT NULL, email VARCHAR(254) NOT NULL, normalized_email VARCHAR(254) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', metadata JSONB DEFAULT '{}' NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, archived_at TIMESTAMP WITH TIME ZONE,
 UNIQUE(organization_id, normalized_email)
);
CREATE TABLE credentials (
 user_id UUID PRIMARY KEY REFERENCES users(id), password_hash VARCHAR(255) NOT NULL,
 password_changed_at TIMESTAMP WITH TIME ZONE NOT NULL, failed_login_count INTEGER DEFAULT 0 NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE roles (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id), code VARCHAR(40) NOT NULL,
 display_name VARCHAR(120) NOT NULL, description TEXT, status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE(organization_id,code)
);
CREATE TABLE permissions (id UUID PRIMARY KEY, code VARCHAR(120) UNIQUE NOT NULL, description TEXT NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE TABLE user_role_grants (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id), user_id UUID NOT NULL REFERENCES users(id),
 role_id UUID NOT NULL REFERENCES roles(id), granted_by_user_id UUID REFERENCES users(id),
 granted_at TIMESTAMP WITH TIME ZONE NOT NULL, revoked_at TIMESTAMP WITH TIME ZONE,
 UNIQUE(user_id,role_id,granted_at)
);
CREATE TABLE role_permissions (
 role_id UUID NOT NULL REFERENCES roles(id), permission_id UUID NOT NULL REFERENCES permissions(id),
 granted_at TIMESTAMP WITH TIME ZONE NOT NULL, PRIMARY KEY(role_id,permission_id)
);
CREATE TABLE departments (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id), code VARCHAR(60) NOT NULL,
 name VARCHAR(160) NOT NULL, status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(organization_id,code)
);
CREATE TABLE teams (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id), code VARCHAR(60) NOT NULL,
 name VARCHAR(160) NOT NULL, description TEXT, manager_user_id UUID NOT NULL REFERENCES users(id),
 department_id UUID NOT NULL REFERENCES departments(id), status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE(organization_id,code)
);
CREATE TABLE team_memberships (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id), team_id UUID NOT NULL REFERENCES teams(id),
 user_id UUID NOT NULL REFERENCES users(id), joined_at TIMESTAMP WITH TIME ZONE NOT NULL,
 left_at TIMESTAMP WITH TIME ZONE, status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL, UNIQUE(team_id,user_id,joined_at)
);
CREATE TABLE offering_categories (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id), code VARCHAR(80) NOT NULL,
 name VARCHAR(160) NOT NULL, description TEXT, status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(organization_id,code)
);
CREATE TABLE offerings (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id), category_id UUID NOT NULL REFERENCES offering_categories(id),
 code VARCHAR(80) NOT NULL, name VARCHAR(200) NOT NULL, description TEXT,
 kind VARCHAR(20) NOT NULL, unit VARCHAR(80), status VARCHAR(20) NOT NULL,
 metadata JSONB DEFAULT '{}' NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL, UNIQUE(organization_id,code)
);
CREATE TABLE offering_prices (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id), offering_id UUID NOT NULL REFERENCES offerings(id),
 amount NUMERIC(18,2) NOT NULL, currency_code CHAR(3) NOT NULL, pricing_unit VARCHAR(80),
 effective_from TIMESTAMP WITH TIME ZONE NOT NULL, effective_to TIMESTAMP WITH TIME ZONE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE sales_assignments (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id), offering_id UUID NOT NULL REFERENCES offerings(id),
 sales_user_id UUID NOT NULL REFERENCES users(id), assigned_by_user_id UUID NOT NULL REFERENCES users(id),
 status VARCHAR(20) NOT NULL, effective_from TIMESTAMP WITH TIME ZONE NOT NULL, effective_to TIMESTAMP WITH TIME ZONE,
 revoked_at TIMESTAMP WITH TIME ZONE, revoke_reason TEXT, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE audit_logs (
 id UUID PRIMARY KEY, organization_id UUID NOT NULL REFERENCES organizations(id), actor_user_id UUID NOT NULL REFERENCES users(id),
 action VARCHAR(120) NOT NULL, entity_type VARCHAR(100) NOT NULL, entity_id UUID NOT NULL,
 before_data JSONB DEFAULT '{}' NOT NULL, after_data JSONB DEFAULT '{}' NOT NULL,
 safe_metadata JSONB DEFAULT '{}' NOT NULL, result VARCHAR(30) NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
