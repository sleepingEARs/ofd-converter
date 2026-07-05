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
