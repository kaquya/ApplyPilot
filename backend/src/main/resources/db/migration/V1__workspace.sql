CREATE TABLE accounts (
  id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE, name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100), google_subject VARCHAR(255) UNIQUE,
  plan VARCHAR(20) NOT NULL DEFAULT 'FREE', stripe_customer VARCHAR(100),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE workspace_entries (
  id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  kind VARCHAR(20) NOT NULL, payload TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX entries_owner_kind ON workspace_entries(owner_id, kind);
CREATE TABLE documents (
  id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  entry_id UUID REFERENCES workspace_entries(id) ON DELETE CASCADE,
  filename VARCHAR(255) NOT NULL, content_type VARCHAR(100) NOT NULL, size_bytes BIGINT NOT NULL,
  storage_key VARCHAR(300) NOT NULL, extracted_text TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX documents_owner ON documents(owner_id);
CREATE TABLE ai_usage (
  id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX usage_owner_date ON ai_usage(owner_id, created_at);
CREATE TABLE billing_events (id VARCHAR(255) PRIMARY KEY, created_at TIMESTAMP WITH TIME ZONE NOT NULL);
