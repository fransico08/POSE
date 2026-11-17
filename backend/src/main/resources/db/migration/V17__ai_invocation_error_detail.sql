ALTER TABLE ai_invocations ADD COLUMN error_detail VARCHAR(40);

ALTER TABLE ai_invocations ADD CONSTRAINT ck_ai_invocation_error_detail CHECK (
    error_detail IS NULL OR (
        error_code IS NOT NULL AND error_code = 'INVALID_OUTPUT'
        AND error_detail IN ('UNKNOWN_URL', 'UNKNOWN_UUID', 'SYSTEM_PROMPT_LEAK', 'CONTACT_INFO')
    )
);
