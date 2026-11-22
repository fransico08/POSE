-- DEC-35: record a non-sensitive reason code for every INVALID_OUTPUT rejection.
-- Codes classify why the gateway rejected model output; no model text is stored.
ALTER TABLE ai_invocations DROP CONSTRAINT ck_ai_invocation_error_detail;

ALTER TABLE ai_invocations ADD CONSTRAINT ck_ai_invocation_error_detail CHECK (
    error_detail IS NULL OR (
        error_code IS NOT NULL AND error_code = 'INVALID_OUTPUT'
        AND error_detail IN (
            'UNKNOWN_URL', 'UNKNOWN_UUID', 'SYSTEM_PROMPT_LEAK', 'CONTACT_INFO',
            'EMPTY_OUTPUT', 'NOT_JSON', 'SCHEMA_MISMATCH', 'NO_VALID_SOURCE',
            'MASK_TOKEN', 'TOO_LONG', 'SIGNAL_REJECTED'
        )
    )
);
