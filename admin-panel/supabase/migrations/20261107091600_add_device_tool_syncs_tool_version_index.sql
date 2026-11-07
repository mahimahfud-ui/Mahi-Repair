-- The FK device_tool_syncs(tool_id, version) references the composite
-- repair_tool_versions(tool_id, version) key. Index both referencing columns
-- in FK order; this also supports tool_id-only lookups through the left prefix.
CREATE INDEX IF NOT EXISTS device_tool_syncs_tool_version_idx
  ON public.device_tool_syncs(tool_id, version);

-- The composite index subsumes the previous tool_id-only index.
DROP INDEX IF EXISTS public.device_tool_syncs_tool_id_idx;
