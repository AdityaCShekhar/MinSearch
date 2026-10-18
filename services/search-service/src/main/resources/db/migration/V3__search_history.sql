CREATE TABLE search_history (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  query TEXT NOT NULL,
  normalized_query TEXT NOT NULL,
  result_count BIGINT NOT NULL,
  latency_ms INTEGER NOT NULL CHECK (latency_ms >= 0),
  succeeded BOOLEAN NOT NULL,
  searched_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_search_history_user_time ON search_history(user_id, searched_at DESC);
CREATE INDEX idx_search_history_time ON search_history(searched_at);
CREATE INDEX idx_search_history_trending ON search_history(normalized_query, searched_at) WHERE succeeded = true;
