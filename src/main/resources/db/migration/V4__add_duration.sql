-- Restore the `duration` column dropped in V2.
--
-- V2 removed it as unused: no entity field referenced it, so ddl-auto=validate
-- failed against the V1 schema. The create-event API reinstates it, because the
-- client's CreateShowRequest sends a duration and the alternative is accepting
-- that value and throwing it away.
ALTER TABLE events ADD COLUMN duration VARCHAR(255) DEFAULT NULL;
