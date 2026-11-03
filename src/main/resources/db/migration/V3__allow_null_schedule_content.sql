-- This migration is approved for test1 only; fail before DDL on any other schema.
DO $$
BEGIN
    IF current_schema() IS DISTINCT FROM 'test1' THEN
        RAISE EXCEPTION 'V3 is restricted to test1';
    END IF;
END $$;

ALTER TABLE test1.schedule_details ALTER COLUMN schedule_content DROP NOT NULL;
