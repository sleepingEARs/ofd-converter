CREATE TABLE IF NOT EXISTS task (
  id TEXT PRIMARY KEY,
  source_file_id TEXT NOT NULL,
  source_filename TEXT NOT NULL,
  source_type TEXT NOT NULL,
  target_format TEXT NOT NULL,
  status TEXT NOT NULL,
  options_json TEXT,
  output_path TEXT,
  output_filename TEXT,
  output_size INTEGER,
  output_type TEXT,
  error_message TEXT,
  downloaded_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_task_created ON task(created_at);

CREATE TABLE IF NOT EXISTS operation_log (
  id TEXT PRIMARY KEY,
  operation_type TEXT NOT NULL,
  client_ip TEXT,
  file_id TEXT,
  task_id TEXT,
  target_format TEXT,
  status TEXT NOT NULL,
  duration_ms INTEGER,
  error_message TEXT,
  user_agent TEXT,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_log_created ON operation_log(created_at);
