-- Requires Nhi's corrected V1 identity baseline (organizations, users and audit_logs).
CREATE TABLE auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_auth_session_expiry CHECK (expires_at > created_at)
);

CREATE TABLE auth_refresh_tokens (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES auth_sessions(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_auth_refresh_hash CHECK (length(token_hash) = 64),
    CONSTRAINT ck_auth_refresh_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_auth_sessions_user ON auth_sessions(organization_id, user_id);
CREATE INDEX idx_auth_sessions_expiry ON auth_sessions(expires_at);
CREATE INDEX idx_auth_refresh_session ON auth_refresh_tokens(session_id);
