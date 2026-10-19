-- Re-enable and validate the foreign keys disabled for the data-only import.
-- Validation fails if the recovered data violates a relationship.

WHENEVER OSERROR EXIT FAILURE ROLLBACK
WHENEVER SQLERROR EXIT FAILURE ROLLBACK

SET ECHO OFF
SET FEEDBACK OFF
SET HEADING OFF
SET PAGESIZE 0
SET SERVEROUTPUT ON SIZE UNLIMITED

BEGIN
  FOR constraint_row IN (
    SELECT table_name, constraint_name
    FROM user_constraints
    WHERE constraint_type = 'R'
    ORDER BY table_name, constraint_name
  ) LOOP
    EXECUTE IMMEDIATE 'ALTER TABLE "' || constraint_row.table_name ||
                      '" ENABLE VALIDATE CONSTRAINT "' || constraint_row.constraint_name || '"';
  END LOOP;
END;
/

PROMPT FOREIGN_KEYS_ENABLED_AND_VALIDATED
EXIT SUCCESS
