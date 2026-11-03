-- V3 remains immutable. Run this migration only after the approved V3 handling.
DO $$
DECLARE
    target_schema text := current_schema();
    column_type oid;
    column_typmod integer;
    column_not_null boolean;
BEGIN
    IF target_schema IS NULL OR target_schema NOT IN ('public', 'test1') THEN
        RAISE EXCEPTION 'V4 requires public or test1';
    END IF;

    -- Inspect the exact schema, ordinary table and live column; do not use search_path fallback.
    SELECT a.atttypid, a.atttypmod, a.attnotnull
      INTO column_type, column_typmod, column_not_null
      FROM pg_catalog.pg_attribute a
      JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
      JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname = target_schema
       AND c.relname = 'schedule_details'
       AND c.relkind = 'r'
       AND a.attname = 'schedule_content'
       AND a.attnum > 0
       AND NOT a.attisdropped;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'V4 requires schedule_details.schedule_content on an ordinary table';
    END IF;
    -- PostgreSQL varchar(n) stores n + VARHDRSZ (4) as atttypmod.
    IF column_type IS DISTINCT FROM 'pg_catalog.varchar'::pg_catalog.regtype
       OR column_typmod IS DISTINCT FROM 54 THEN
        RAISE EXCEPTION 'V4 requires schedule_content to be VARCHAR(50)';
    END IF;

    IF target_schema = 'test1' THEN
        IF column_not_null THEN
            RAISE EXCEPTION 'V4 requires test1 V3 to have already allowed NULL';
        END IF;
        -- Already nullable: no DDL and no data changes in test1.
        RETURN;
    END IF;

    IF column_not_null THEN
        ALTER TABLE public.schedule_details ALTER COLUMN schedule_content DROP NOT NULL;
    END IF;
END $$;
