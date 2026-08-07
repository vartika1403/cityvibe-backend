-- Drop the unused `duration` column from events.
-- The Event entity no longer declares this field, so ddl-auto=validate
-- would fail against the V1 schema until this column is removed.
ALTER TABLE events DROP COLUMN duration;
