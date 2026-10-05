-- Schema-only snapshot of Nhi's POSE_CRM_DB_v2_2026-10-01.backup (2026-10-01).
-- No demo data, credentials, ownership or psql session directives.
--
-- PostgreSQL database dump
--


-- Dumped from database version 17.10
-- Dumped by pg_dump version 17.10


--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

-- *not* creating schema, since initdb creates it


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS '';




--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    action character varying(120) NOT NULL,
    entity_type character varying(100) NOT NULL,
    entity_id uuid NOT NULL,
    before_data jsonb DEFAULT '{}'::jsonb NOT NULL,
    after_data jsonb DEFAULT '{}'::jsonb NOT NULL,
    safe_metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    result character varying(30) NOT NULL,
    occurred_at timestamp with time zone NOT NULL
);


--
-- Name: credentials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.credentials (
    user_id uuid NOT NULL,
    password_hash character varying(255) NOT NULL,
    password_changed_at timestamp with time zone NOT NULL,
    failed_login_count integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_credentials_failures CHECK ((failed_login_count >= 0))
);


--
-- Name: crm_tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.crm_tasks (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid,
    lead_id uuid,
    opportunity_id uuid,
    owner_user_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    title character varying(220) NOT NULL,
    description text,
    priority character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    due_at timestamp with time zone NOT NULL,
    result text,
    origin character varying(30) NOT NULL,
    idempotency_key character varying(160),
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_task_complete CHECK (((((status)::text = 'DONE'::text) AND (completed_at IS NOT NULL)) OR ((status)::text <> 'DONE'::text))),
    CONSTRAINT ck_task_origin CHECK (((origin)::text = ANY ((ARRAY['MANUAL'::character varying, 'WORKFLOW'::character varying, 'IMPORT'::character varying])::text[]))),
    CONSTRAINT ck_task_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying, 'DONE'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_task_subject_xor CHECK (((customer_id IS NOT NULL) <> (lead_id IS NOT NULL)))
);


--
-- Name: customer_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_assignments (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    user_id uuid NOT NULL,
    assignment_type character varying(20) NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    effective_to timestamp with time zone,
    status character varying(20) NOT NULL,
    assigned_by_user_id uuid NOT NULL,
    source character varying(30) NOT NULL,
    handover_request_id uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_customer_assignment_dates CHECK (((effective_to IS NULL) OR (effective_to >= effective_from))),
    CONSTRAINT ck_customer_assignment_source CHECK (((source)::text = ANY ((ARRAY['INITIAL'::character varying, 'MANUAL'::character varying, 'HANDOVER'::character varying, 'IMPORT'::character varying])::text[]))),
    CONSTRAINT ck_customer_assignment_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ENDED'::character varying])::text[]))),
    CONSTRAINT ck_customer_assignment_type CHECK (((assignment_type)::text = ANY ((ARRAY['SALES'::character varying, 'CARE'::character varying])::text[])))
);


--
-- Name: TABLE customer_assignments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.customer_assignments IS 'Physical persistence for Customer SALES/CARE assignment history and authorization timeline. It supports Handover/Customer Care scope; it is not a one-class-one-table inference from the Domain Class Diagram.';


--
-- Name: customers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customers (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    owner_user_id uuid NOT NULL,
    display_name character varying(180) NOT NULL,
    email character varying(254),
    normalized_email character varying(254),
    phone character varying(40),
    normalized_phone character varying(40),
    status character varying(30) NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    archived_at timestamp with time zone,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_customers_identity CHECK (((normalized_email IS NOT NULL) OR (normalized_phone IS NOT NULL)))
);


--
-- Name: feedbacks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.feedbacks (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid,
    lead_id uuid,
    recorded_by_user_id uuid NOT NULL,
    category character varying(60) NOT NULL,
    content text NOT NULL,
    rating integer,
    visibility character varying(20) NOT NULL,
    received_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_feedback_rating CHECK (((rating IS NULL) OR ((rating >= 1) AND (rating <= 5)))),
    CONSTRAINT ck_feedback_subject_xor CHECK (((customer_id IS NOT NULL) <> (lead_id IS NOT NULL))),
    CONSTRAINT ck_feedback_visibility CHECK (((visibility)::text = ANY ((ARRAY['CARE_VISIBLE'::character varying, 'SALES_ONLY'::character varying])::text[])))
);


--
-- Name: interactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.interactions (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid,
    lead_id uuid,
    opportunity_id uuid,
    recorded_by_user_id uuid NOT NULL,
    type character varying(50) NOT NULL,
    channel character varying(50) NOT NULL,
    subject character varying(220) NOT NULL,
    content text NOT NULL,
    outcome text,
    visibility character varying(20) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_interaction_subject_xor CHECK (((customer_id IS NOT NULL) <> (lead_id IS NOT NULL))),
    CONSTRAINT ck_interaction_visibility CHECK (((visibility)::text = ANY ((ARRAY['CARE_VISIBLE'::character varying, 'SALES_ONLY'::character varying])::text[])))
);


--
-- Name: opportunities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.opportunities (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid,
    lead_id uuid,
    offering_id uuid NOT NULL,
    owner_user_id uuid NOT NULL,
    title character varying(220) NOT NULL,
    stage character varying(30) NOT NULL,
    expected_value numeric(18,2),
    currency_code character(3) DEFAULT 'VND'::bpchar NOT NULL,
    expected_close_date date,
    closing_reason text,
    closed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_opportunity_closed CHECK (((((stage)::text = ANY ((ARRAY['WON'::character varying, 'LOST'::character varying])::text[])) AND (closing_reason IS NOT NULL) AND (closed_at IS NOT NULL)) OR (((stage)::text <> ALL ((ARRAY['WON'::character varying, 'LOST'::character varying])::text[])) AND (closed_at IS NULL)))),
    CONSTRAINT ck_opportunity_stage CHECK (((stage)::text = ANY ((ARRAY['NEW'::character varying, 'QUALIFIED'::character varying, 'PROPOSAL'::character varying, 'WON'::character varying, 'LOST'::character varying])::text[]))),
    CONSTRAINT ck_opportunity_subject_xor CHECK (((customer_id IS NOT NULL) <> (lead_id IS NOT NULL))),
    CONSTRAINT ck_opportunity_value CHECK (((expected_value IS NULL) OR (expected_value >= (0)::numeric)))
);


--
-- Name: source_record_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_record_links (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_system_id uuid NOT NULL,
    subject_type character varying(20) NOT NULL,
    subject_id uuid NOT NULL,
    external_id character varying(180) NOT NULL,
    source_entity_type character varying(60) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    linked_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_source_record_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ARCHIVED'::character varying])::text[]))),
    CONSTRAINT ck_source_record_subject CHECK (((subject_type)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'LEAD'::character varying])::text[])))
);


--
-- Name: TABLE source_record_links; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.source_record_links IS 'Polymorphic physical mapping for SourceRecordLink. subject_id is validated by backend/seed validator according to subject_type.';


--
-- Name: support_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.support_requests (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    assigned_to_user_id uuid NOT NULL,
    title character varying(220) NOT NULL,
    category character varying(80) NOT NULL,
    description text NOT NULL,
    priority character varying(20) NOT NULL,
    status character varying(30) NOT NULL,
    escalation_reason text,
    resolution text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    resolved_at timestamp with time zone,
    closed_at timestamp with time zone,
    CONSTRAINT ck_support_escalated CHECK ((((status)::text <> 'ESCALATED'::text) OR (escalation_reason IS NOT NULL))),
    CONSTRAINT ck_support_priority CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'URGENT'::character varying])::text[]))),
    CONSTRAINT ck_support_resolution CHECK ((((status)::text <> ALL ((ARRAY['RESOLVED'::character varying, 'CLOSED'::character varying])::text[])) OR (resolution IS NOT NULL))),
    CONSTRAINT ck_support_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying, 'ESCALATED'::character varying, 'RESOLVED'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: customer_360_summary; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.customer_360_summary AS
 SELECT c.id AS customer_id,
    c.organization_id,
    c.owner_user_id,
    care.user_id AS care_user_id,
    c.display_name,
    c.normalized_email,
    c.normalized_phone,
    ( SELECT count(*) AS count
           FROM public.source_record_links s
          WHERE (((s.subject_type)::text = 'CUSTOMER'::text) AND (s.subject_id = c.id) AND ((s.status)::text = 'ACTIVE'::text))) AS source_count,
    ( SELECT count(*) AS count
           FROM public.opportunities o
          WHERE (o.customer_id = c.id)) AS opportunity_count,
    ( SELECT count(*) AS count
           FROM public.interactions i
          WHERE (i.customer_id = c.id)) AS interaction_count,
    ( SELECT count(*) AS count
           FROM public.feedbacks f
          WHERE (f.customer_id = c.id)) AS feedback_count,
    ( SELECT count(*) AS count
           FROM public.crm_tasks t
          WHERE ((t.customer_id = c.id) AND ((t.status)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying])::text[])))) AS open_task_count,
    ( SELECT count(*) AS count
           FROM public.support_requests sr
          WHERE ((sr.customer_id = c.id) AND ((sr.status)::text <> ALL ((ARRAY['RESOLVED'::character varying, 'CLOSED'::character varying])::text[])))) AS open_support_count,
    ( SELECT max(i.occurred_at) AS max
           FROM public.interactions i
          WHERE (i.customer_id = c.id)) AS last_interaction_at,
    CURRENT_TIMESTAMP AS refreshed_at
   FROM (public.customers c
     LEFT JOIN LATERAL ( SELECT ca.user_id
           FROM public.customer_assignments ca
          WHERE ((ca.customer_id = c.id) AND ((ca.assignment_type)::text = 'CARE'::text) AND ((ca.status)::text = 'ACTIVE'::text))
          ORDER BY ca.effective_from DESC
         LIMIT 1) care ON (true));


--
-- Name: departments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.departments (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    code character varying(60) NOT NULL,
    name character varying(160) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_departments_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: duplicate_review_cases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.duplicate_review_cases (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    subject_type character varying(20) NOT NULL,
    subject_id uuid NOT NULL,
    candidate_type character varying(20) NOT NULL,
    candidate_id uuid NOT NULL,
    match_reason text NOT NULL,
    status character varying(30) NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_duplicate_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CONFIRMED_MATCH'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT ck_duplicate_subject CHECK ((((subject_type)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'LEAD'::character varying])::text[])) AND ((candidate_type)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'LEAD'::character varying])::text[]))))
);


--
-- Name: TABLE duplicate_review_cases; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.duplicate_review_cases IS 'Polymorphic suspected-duplicate review. subject/candidate references are validated by backend/seed validator.';


--
-- Name: handover_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.handover_requests (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    handover_type character varying(20) NOT NULL,
    requested_by_user_id uuid NOT NULL,
    target_user_id uuid,
    reason text NOT NULL,
    status character varying(20) NOT NULL,
    reviewed_by_user_id uuid,
    reviewed_at timestamp with time zone,
    rejection_reason text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_handover_review CHECK (((((status)::text = 'PENDING'::text) AND (reviewed_by_user_id IS NULL) AND (reviewed_at IS NULL)) OR (((status)::text = 'APPROVED'::text) AND (reviewed_by_user_id IS NOT NULL) AND (reviewed_at IS NOT NULL) AND (target_user_id IS NOT NULL)) OR (((status)::text = 'REJECTED'::text) AND (reviewed_by_user_id IS NOT NULL) AND (reviewed_at IS NOT NULL) AND (rejection_reason IS NOT NULL)))),
    CONSTRAINT ck_handover_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT ck_handover_type CHECK (((handover_type)::text = ANY ((ARRAY['SALES'::character varying, 'CARE'::character varying])::text[])))
);


--
-- Name: import_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.import_decisions (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    import_job_id uuid NOT NULL,
    decision character varying(20) NOT NULL,
    decided_by_user_id uuid NOT NULL,
    decided_at timestamp with time zone NOT NULL,
    reason text,
    CONSTRAINT ck_import_decision CHECK (((decision)::text = ANY ((ARRAY['APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: import_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.import_jobs (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    source_system_id uuid NOT NULL,
    submitted_by_user_id uuid NOT NULL,
    approved_by_user_id uuid,
    status character varying(30) NOT NULL,
    original_file_ref text NOT NULL,
    original_filename character varying(255) NOT NULL,
    file_checksum character varying(128) NOT NULL,
    idempotency_key character varying(180) NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    total_rows integer DEFAULT 0 NOT NULL,
    valid_rows integer DEFAULT 0 NOT NULL,
    error_rows integer DEFAULT 0 NOT NULL,
    submitted_at timestamp with time zone NOT NULL,
    validated_at timestamp with time zone,
    approved_at timestamp with time zone,
    committed_at timestamp with time zone,
    failure_reason text,
    CONSTRAINT ck_import_job_approval CHECK (((((status)::text = 'COMMITTED'::text) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (committed_at IS NOT NULL)) OR ((status)::text <> 'COMMITTED'::text))),
    CONSTRAINT ck_import_job_counts CHECK (((retry_count >= 0) AND (total_rows >= 0) AND (valid_rows >= 0) AND (error_rows >= 0))),
    CONSTRAINT ck_import_job_status CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'VALIDATING'::character varying, 'REVIEW_REQUIRED'::character varying, 'COMMITTED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: import_rows; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.import_rows (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    import_job_id uuid NOT NULL,
    row_number integer NOT NULL,
    raw_payload jsonb NOT NULL,
    normalized_payload jsonb,
    status character varying(20) NOT NULL,
    error_codes jsonb DEFAULT '[]'::jsonb NOT NULL,
    duplicate_candidate boolean DEFAULT false NOT NULL,
    committed_entity_type character varying(20),
    committed_entity_id uuid,
    row_fingerprint character varying(128) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_import_row_committed_pair CHECK (((committed_entity_type IS NULL) = (committed_entity_id IS NULL))),
    CONSTRAINT ck_import_row_entity_type CHECK (((committed_entity_type IS NULL) OR ((committed_entity_type)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'LEAD'::character varying])::text[])))),
    CONSTRAINT ck_import_row_status CHECK (((status)::text = ANY ((ARRAY['VALID'::character varying, 'INVALID'::character varying, 'DUPLICATE'::character varying, 'SKIPPED'::character varying])::text[])))
);


--
-- Name: leads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.leads (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    owner_user_id uuid NOT NULL,
    offering_id uuid NOT NULL,
    display_name character varying(180) NOT NULL,
    email character varying(254),
    normalized_email character varying(254),
    phone character varying(40),
    normalized_phone character varying(40),
    status character varying(30) NOT NULL,
    qualified_at timestamp with time zone,
    converted_customer_id uuid,
    converted_at timestamp with time zone,
    disqualification_reason text,
    created_at timestamp with time zone NOT NULL,
    archived_at timestamp with time zone,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_leads_conversion CHECK (((((status)::text = 'CONVERTED'::text) AND (converted_customer_id IS NOT NULL) AND (converted_at IS NOT NULL)) OR (((status)::text <> 'CONVERTED'::text) AND (converted_customer_id IS NULL) AND (converted_at IS NULL)))),
    CONSTRAINT ck_leads_disqualified CHECK ((((status)::text <> 'DISQUALIFIED'::text) OR (disqualification_reason IS NOT NULL))),
    CONSTRAINT ck_leads_identity CHECK (((normalized_email IS NOT NULL) OR (normalized_phone IS NOT NULL))),
    CONSTRAINT ck_leads_status CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'QUALIFIED'::character varying, 'CONVERTED'::character varying, 'DISQUALIFIED'::character varying])::text[])))
);


--
-- Name: offering_attribute_definitions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offering_attribute_definitions (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    category_id uuid NOT NULL,
    key character varying(100) NOT NULL,
    label character varying(160) NOT NULL,
    value_type character varying(30) NOT NULL,
    required boolean DEFAULT false NOT NULL,
    validation_rule text,
    options jsonb,
    sort_order integer DEFAULT 0 NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT ck_attribute_def_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[]))),
    CONSTRAINT ck_attribute_def_type CHECK (((value_type)::text = ANY ((ARRAY['TEXT'::character varying, 'NUMBER'::character varying, 'BOOLEAN'::character varying, 'OPTION'::character varying, 'DATE'::character varying])::text[])))
);


--
-- Name: offering_attribute_values; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offering_attribute_values (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    offering_id uuid NOT NULL,
    attribute_definition_id uuid NOT NULL,
    value_json jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


--
-- Name: offering_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offering_categories (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    code character varying(80) NOT NULL,
    name character varying(160) NOT NULL,
    description text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_offering_categories_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: offering_prices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offering_prices (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    offering_id uuid NOT NULL,
    amount numeric(18,2) NOT NULL,
    currency_code character(3) NOT NULL,
    pricing_unit character varying(80),
    effective_from timestamp with time zone NOT NULL,
    effective_to timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_offering_prices_amount CHECK ((amount >= (0)::numeric)),
    CONSTRAINT ck_offering_prices_dates CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)))
);


--
-- Name: offerings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.offerings (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    category_id uuid NOT NULL,
    code character varying(80) NOT NULL,
    name character varying(200) NOT NULL,
    description text,
    kind character varying(20) NOT NULL,
    unit character varying(80),
    status character varying(20) NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_offerings_kind CHECK (((kind)::text = ANY ((ARRAY['PRODUCT'::character varying, 'SERVICE'::character varying])::text[]))),
    CONSTRAINT ck_offerings_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: organizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organizations (
    id uuid NOT NULL,
    name character varying(160) NOT NULL,
    industry character varying(120),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_organizations_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: outbox_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outbox_events (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    aggregate_type character varying(100) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type character varying(120) NOT NULL,
    payload jsonb NOT NULL,
    idempotency_key character varying(180) NOT NULL,
    delivery_status character varying(20) NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    last_error text,
    occurred_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    CONSTRAINT ck_outbox_retry CHECK ((retry_count >= 0)),
    CONSTRAINT ck_outbox_status CHECK (((delivery_status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    id uuid NOT NULL,
    code character varying(120) NOT NULL,
    description text NOT NULL,
    created_at timestamp with time zone NOT NULL
);


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL,
    granted_at timestamp with time zone NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    code character varying(40) NOT NULL,
    display_name character varying(120) NOT NULL,
    description text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_roles_code CHECK (((code)::text = ANY ((ARRAY['ADMIN'::character varying, 'MANAGER'::character varying, 'SALES_REP'::character varying, 'CUSTOMER_CARE'::character varying, 'DATA_STAFF'::character varying])::text[]))),
    CONSTRAINT ck_roles_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: sales_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales_assignments (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    offering_id uuid NOT NULL,
    sales_user_id uuid NOT NULL,
    assigned_by_user_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    effective_to timestamp with time zone,
    revoked_at timestamp with time zone,
    revoke_reason text,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_sales_assignment_dates CHECK (((effective_to IS NULL) OR (effective_to >= effective_from))),
    CONSTRAINT ck_sales_assignment_revoked CHECK ((((status)::text <> 'REVOKED'::text) OR (revoked_at IS NOT NULL))),
    CONSTRAINT ck_sales_assignment_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'EXPIRED'::character varying, 'REVOKED'::character varying])::text[])))
);


--
-- Name: source_systems; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.source_systems (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    code character varying(80) NOT NULL,
    name character varying(160) NOT NULL,
    source_type character varying(30) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_source_systems_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: support_attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.support_attachments (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    support_request_id uuid NOT NULL,
    support_response_id uuid,
    file_name character varying(255) NOT NULL,
    file_type character varying(120) NOT NULL,
    file_size bigint NOT NULL,
    storage_ref text NOT NULL,
    uploaded_by_user_id uuid NOT NULL,
    uploaded_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_support_attachment_size CHECK ((file_size > 0))
);


--
-- Name: support_responses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.support_responses (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    support_request_id uuid NOT NULL,
    content text NOT NULL,
    status_after character varying(30),
    created_by_user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_support_response_status CHECK (((status_after IS NULL) OR ((status_after)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying, 'ESCALATED'::character varying, 'RESOLVED'::character varying, 'CLOSED'::character varying])::text[]))))
);


--
-- Name: team_memberships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.team_memberships (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    team_id uuid NOT NULL,
    user_id uuid NOT NULL,
    joined_at timestamp with time zone NOT NULL,
    left_at timestamp with time zone,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT ck_team_membership_dates CHECK (((left_at IS NULL) OR (left_at >= joined_at))),
    CONSTRAINT ck_team_membership_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ENDED'::character varying])::text[])))
);


--
-- Name: teams; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teams (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    code character varying(60) NOT NULL,
    name character varying(160) NOT NULL,
    description text,
    manager_user_id uuid NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    department_id uuid NOT NULL,
    CONSTRAINT ck_teams_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: user_role_grants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_role_grants (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    granted_by_user_id uuid,
    granted_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    CONSTRAINT ck_user_role_grant_dates CHECK (((revoked_at IS NULL) OR (revoked_at >= granted_at)))
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    full_name character varying(160) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    email character varying(254) NOT NULL,
    normalized_email character varying(254) NOT NULL,
    archived_at timestamp with time zone,
    CONSTRAINT ck_users_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: workflow_executions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workflow_executions (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    outbox_event_id uuid,
    workflow_type character varying(120) NOT NULL,
    idempotency_key character varying(180) NOT NULL,
    status character varying(20) NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    last_error text,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    created_task_id uuid,
    CONSTRAINT ck_workflow_retry CHECK ((retry_count >= 0)),
    CONSTRAINT ck_workflow_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'DONE'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: credentials credentials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credentials
    ADD CONSTRAINT credentials_pkey PRIMARY KEY (user_id);


--
-- Name: crm_tasks crm_tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_tasks
    ADD CONSTRAINT crm_tasks_pkey PRIMARY KEY (id);


--
-- Name: customer_assignments customer_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_assignments
    ADD CONSTRAINT customer_assignments_pkey PRIMARY KEY (id);


--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (id);


--
-- Name: departments departments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_pkey PRIMARY KEY (id);


--
-- Name: duplicate_review_cases duplicate_review_cases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.duplicate_review_cases
    ADD CONSTRAINT duplicate_review_cases_pkey PRIMARY KEY (id);


--
-- Name: feedbacks feedbacks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedbacks
    ADD CONSTRAINT feedbacks_pkey PRIMARY KEY (id);


--
-- Name: handover_requests handover_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.handover_requests
    ADD CONSTRAINT handover_requests_pkey PRIMARY KEY (id);


--
-- Name: import_decisions import_decisions_import_job_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_decisions
    ADD CONSTRAINT import_decisions_import_job_id_key UNIQUE (import_job_id);


--
-- Name: import_decisions import_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_decisions
    ADD CONSTRAINT import_decisions_pkey PRIMARY KEY (id);


--
-- Name: import_jobs import_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_jobs
    ADD CONSTRAINT import_jobs_pkey PRIMARY KEY (id);


--
-- Name: import_rows import_rows_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_rows
    ADD CONSTRAINT import_rows_pkey PRIMARY KEY (id);


--
-- Name: interactions interactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.interactions
    ADD CONSTRAINT interactions_pkey PRIMARY KEY (id);


--
-- Name: leads leads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT leads_pkey PRIMARY KEY (id);


--
-- Name: offering_attribute_definitions offering_attribute_definitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_attribute_definitions
    ADD CONSTRAINT offering_attribute_definitions_pkey PRIMARY KEY (id);


--
-- Name: offering_attribute_values offering_attribute_values_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_attribute_values
    ADD CONSTRAINT offering_attribute_values_pkey PRIMARY KEY (id);


--
-- Name: offering_categories offering_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_categories
    ADD CONSTRAINT offering_categories_pkey PRIMARY KEY (id);


--
-- Name: offering_prices offering_prices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_prices
    ADD CONSTRAINT offering_prices_pkey PRIMARY KEY (id);


--
-- Name: offerings offerings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offerings
    ADD CONSTRAINT offerings_pkey PRIMARY KEY (id);


--
-- Name: opportunities opportunities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.opportunities
    ADD CONSTRAINT opportunities_pkey PRIMARY KEY (id);


--
-- Name: organizations organizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_pkey PRIMARY KEY (id);


--
-- Name: outbox_events outbox_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_code_key UNIQUE (code);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: sales_assignments sales_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_assignments
    ADD CONSTRAINT sales_assignments_pkey PRIMARY KEY (id);


--
-- Name: source_record_links source_record_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_record_links
    ADD CONSTRAINT source_record_links_pkey PRIMARY KEY (id);


--
-- Name: source_systems source_systems_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_systems
    ADD CONSTRAINT source_systems_pkey PRIMARY KEY (id);


--
-- Name: support_attachments support_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_attachments
    ADD CONSTRAINT support_attachments_pkey PRIMARY KEY (id);


--
-- Name: support_requests support_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_requests
    ADD CONSTRAINT support_requests_pkey PRIMARY KEY (id);


--
-- Name: support_responses support_responses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_responses
    ADD CONSTRAINT support_responses_pkey PRIMARY KEY (id);


--
-- Name: team_memberships team_memberships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_memberships
    ADD CONSTRAINT team_memberships_pkey PRIMARY KEY (id);


--
-- Name: teams teams_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_pkey PRIMARY KEY (id);


--
-- Name: offering_attribute_definitions uq_attribute_def_category_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_attribute_definitions
    ADD CONSTRAINT uq_attribute_def_category_key UNIQUE (category_id, key);


--
-- Name: departments uq_departments_org_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT uq_departments_org_code UNIQUE (organization_id, code);


--
-- Name: import_jobs uq_import_job_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_jobs
    ADD CONSTRAINT uq_import_job_key UNIQUE (organization_id, idempotency_key);


--
-- Name: import_rows uq_import_row_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_rows
    ADD CONSTRAINT uq_import_row_number UNIQUE (import_job_id, row_number);


--
-- Name: offering_attribute_values uq_offering_attribute_value; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_attribute_values
    ADD CONSTRAINT uq_offering_attribute_value UNIQUE (offering_id, attribute_definition_id);


--
-- Name: offering_categories uq_offering_categories_org_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_categories
    ADD CONSTRAINT uq_offering_categories_org_code UNIQUE (organization_id, code);


--
-- Name: offerings uq_offerings_org_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offerings
    ADD CONSTRAINT uq_offerings_org_code UNIQUE (organization_id, code);


--
-- Name: outbox_events uq_outbox_idempotency; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT uq_outbox_idempotency UNIQUE (organization_id, idempotency_key);


--
-- Name: roles uq_roles_org_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT uq_roles_org_code UNIQUE (organization_id, code);


--
-- Name: source_record_links uq_source_record_external; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_record_links
    ADD CONSTRAINT uq_source_record_external UNIQUE (organization_id, source_system_id, source_entity_type, external_id);


--
-- Name: source_systems uq_source_systems_org_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_systems
    ADD CONSTRAINT uq_source_systems_org_code UNIQUE (organization_id, code);


--
-- Name: team_memberships uq_team_membership; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_memberships
    ADD CONSTRAINT uq_team_membership UNIQUE (team_id, user_id, joined_at);


--
-- Name: teams uq_teams_org_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT uq_teams_org_code UNIQUE (organization_id, code);


--
-- Name: user_role_grants uq_user_role_grant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_grants
    ADD CONSTRAINT uq_user_role_grant UNIQUE (user_id, role_id, granted_at);


--
-- Name: users uq_users_org_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_org_email UNIQUE (organization_id, normalized_email);


--
-- Name: workflow_executions uq_workflow_idempotency; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_executions
    ADD CONSTRAINT uq_workflow_idempotency UNIQUE (organization_id, idempotency_key);


--
-- Name: user_role_grants user_role_grants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_grants
    ADD CONSTRAINT user_role_grants_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: workflow_executions workflow_executions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_executions
    ADD CONSTRAINT workflow_executions_pkey PRIMARY KEY (id);


--
-- Name: ix_audit_entity_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_entity_time ON public.audit_logs USING btree (organization_id, entity_type, entity_id, occurred_at DESC);


--
-- Name: ix_customer_assignments_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_customer_assignments_scope ON public.customer_assignments USING btree (customer_id, user_id, assignment_type, effective_from, effective_to);


--
-- Name: ix_customers_org_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_customers_org_owner ON public.customers USING btree (organization_id, owner_user_id);


--
-- Name: ix_import_jobs_org_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_import_jobs_org_status ON public.import_jobs USING btree (organization_id, status);


--
-- Name: ix_interactions_customer_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_interactions_customer_time ON public.interactions USING btree (customer_id, occurred_at DESC);


--
-- Name: ix_interactions_lead_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_interactions_lead_time ON public.interactions USING btree (lead_id, occurred_at DESC);


--
-- Name: ix_leads_org_owner_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_leads_org_owner_status ON public.leads USING btree (organization_id, owner_user_id, status);


--
-- Name: ix_opportunities_org_owner_stage; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_opportunities_org_owner_stage ON public.opportunities USING btree (organization_id, owner_user_id, stage);


--
-- Name: ix_sales_assignments_user_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_sales_assignments_user_status ON public.sales_assignments USING btree (sales_user_id, status, effective_from, effective_to);


--
-- Name: ix_support_assignee_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_support_assignee_status ON public.support_requests USING btree (assigned_to_user_id, status);


--
-- Name: ix_tasks_owner_status_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_tasks_owner_status_due ON public.crm_tasks USING btree (owner_user_id, status, due_at);


--
-- Name: ix_team_memberships_user_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_team_memberships_user_active ON public.team_memberships USING btree (user_id, status);


--
-- Name: ix_users_org_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_users_org_status ON public.users USING btree (organization_id, status);


--
-- Name: uq_customer_active_assignment_type; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_customer_active_assignment_type ON public.customer_assignments USING btree (customer_id, assignment_type) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_customers_org_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_customers_org_email ON public.customers USING btree (organization_id, normalized_email) WHERE (normalized_email IS NOT NULL);


--
-- Name: uq_customers_org_phone; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_customers_org_phone ON public.customers USING btree (organization_id, normalized_phone) WHERE (normalized_phone IS NOT NULL);


--
-- Name: uq_handover_pending_customer; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_handover_pending_customer ON public.handover_requests USING btree (customer_id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: uq_task_idempotency; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_task_idempotency ON public.crm_tasks USING btree (organization_id, idempotency_key) WHERE (idempotency_key IS NOT NULL);


--
-- Name: audit_logs audit_logs_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: audit_logs audit_logs_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: credentials credentials_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credentials
    ADD CONSTRAINT credentials_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: crm_tasks crm_tasks_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_tasks
    ADD CONSTRAINT crm_tasks_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: crm_tasks crm_tasks_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_tasks
    ADD CONSTRAINT crm_tasks_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE RESTRICT;


--
-- Name: crm_tasks crm_tasks_lead_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_tasks
    ADD CONSTRAINT crm_tasks_lead_id_fkey FOREIGN KEY (lead_id) REFERENCES public.leads(id) ON DELETE RESTRICT;


--
-- Name: crm_tasks crm_tasks_opportunity_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_tasks
    ADD CONSTRAINT crm_tasks_opportunity_id_fkey FOREIGN KEY (opportunity_id) REFERENCES public.opportunities(id) ON DELETE RESTRICT;


--
-- Name: crm_tasks crm_tasks_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_tasks
    ADD CONSTRAINT crm_tasks_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: crm_tasks crm_tasks_owner_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.crm_tasks
    ADD CONSTRAINT crm_tasks_owner_user_id_fkey FOREIGN KEY (owner_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: customer_assignments customer_assignments_assigned_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_assignments
    ADD CONSTRAINT customer_assignments_assigned_by_user_id_fkey FOREIGN KEY (assigned_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: customer_assignments customer_assignments_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_assignments
    ADD CONSTRAINT customer_assignments_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE RESTRICT;


--
-- Name: customer_assignments customer_assignments_handover_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_assignments
    ADD CONSTRAINT customer_assignments_handover_request_id_fkey FOREIGN KEY (handover_request_id) REFERENCES public.handover_requests(id) ON DELETE RESTRICT;


--
-- Name: customer_assignments customer_assignments_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_assignments
    ADD CONSTRAINT customer_assignments_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: customer_assignments customer_assignments_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_assignments
    ADD CONSTRAINT customer_assignments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: customers customers_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: customers customers_owner_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_owner_user_id_fkey FOREIGN KEY (owner_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: departments departments_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: duplicate_review_cases duplicate_review_cases_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.duplicate_review_cases
    ADD CONSTRAINT duplicate_review_cases_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: duplicate_review_cases duplicate_review_cases_reviewed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.duplicate_review_cases
    ADD CONSTRAINT duplicate_review_cases_reviewed_by_user_id_fkey FOREIGN KEY (reviewed_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: feedbacks feedbacks_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedbacks
    ADD CONSTRAINT feedbacks_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE RESTRICT;


--
-- Name: feedbacks feedbacks_lead_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedbacks
    ADD CONSTRAINT feedbacks_lead_id_fkey FOREIGN KEY (lead_id) REFERENCES public.leads(id) ON DELETE RESTRICT;


--
-- Name: feedbacks feedbacks_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedbacks
    ADD CONSTRAINT feedbacks_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: feedbacks feedbacks_recorded_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.feedbacks
    ADD CONSTRAINT feedbacks_recorded_by_user_id_fkey FOREIGN KEY (recorded_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: handover_requests handover_requests_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.handover_requests
    ADD CONSTRAINT handover_requests_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE RESTRICT;


--
-- Name: handover_requests handover_requests_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.handover_requests
    ADD CONSTRAINT handover_requests_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: handover_requests handover_requests_requested_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.handover_requests
    ADD CONSTRAINT handover_requests_requested_by_user_id_fkey FOREIGN KEY (requested_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: handover_requests handover_requests_reviewed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.handover_requests
    ADD CONSTRAINT handover_requests_reviewed_by_user_id_fkey FOREIGN KEY (reviewed_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: handover_requests handover_requests_target_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.handover_requests
    ADD CONSTRAINT handover_requests_target_user_id_fkey FOREIGN KEY (target_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: import_decisions import_decisions_decided_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_decisions
    ADD CONSTRAINT import_decisions_decided_by_user_id_fkey FOREIGN KEY (decided_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: import_decisions import_decisions_import_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_decisions
    ADD CONSTRAINT import_decisions_import_job_id_fkey FOREIGN KEY (import_job_id) REFERENCES public.import_jobs(id) ON DELETE RESTRICT;


--
-- Name: import_decisions import_decisions_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_decisions
    ADD CONSTRAINT import_decisions_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: import_jobs import_jobs_approved_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_jobs
    ADD CONSTRAINT import_jobs_approved_by_user_id_fkey FOREIGN KEY (approved_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: import_jobs import_jobs_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_jobs
    ADD CONSTRAINT import_jobs_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: import_jobs import_jobs_source_system_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_jobs
    ADD CONSTRAINT import_jobs_source_system_id_fkey FOREIGN KEY (source_system_id) REFERENCES public.source_systems(id) ON DELETE RESTRICT;


--
-- Name: import_jobs import_jobs_submitted_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_jobs
    ADD CONSTRAINT import_jobs_submitted_by_user_id_fkey FOREIGN KEY (submitted_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: import_rows import_rows_import_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_rows
    ADD CONSTRAINT import_rows_import_job_id_fkey FOREIGN KEY (import_job_id) REFERENCES public.import_jobs(id) ON DELETE RESTRICT;


--
-- Name: import_rows import_rows_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_rows
    ADD CONSTRAINT import_rows_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: interactions interactions_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.interactions
    ADD CONSTRAINT interactions_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE RESTRICT;


--
-- Name: interactions interactions_lead_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.interactions
    ADD CONSTRAINT interactions_lead_id_fkey FOREIGN KEY (lead_id) REFERENCES public.leads(id) ON DELETE RESTRICT;


--
-- Name: interactions interactions_opportunity_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.interactions
    ADD CONSTRAINT interactions_opportunity_id_fkey FOREIGN KEY (opportunity_id) REFERENCES public.opportunities(id) ON DELETE RESTRICT;


--
-- Name: interactions interactions_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.interactions
    ADD CONSTRAINT interactions_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: interactions interactions_recorded_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.interactions
    ADD CONSTRAINT interactions_recorded_by_user_id_fkey FOREIGN KEY (recorded_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: leads leads_converted_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT leads_converted_customer_id_fkey FOREIGN KEY (converted_customer_id) REFERENCES public.customers(id) ON DELETE RESTRICT;


--
-- Name: leads leads_offering_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT leads_offering_id_fkey FOREIGN KEY (offering_id) REFERENCES public.offerings(id) ON DELETE RESTRICT;


--
-- Name: leads leads_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT leads_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: leads leads_owner_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leads
    ADD CONSTRAINT leads_owner_user_id_fkey FOREIGN KEY (owner_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: offering_attribute_definitions offering_attribute_definitions_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_attribute_definitions
    ADD CONSTRAINT offering_attribute_definitions_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.offering_categories(id) ON DELETE RESTRICT;


--
-- Name: offering_attribute_definitions offering_attribute_definitions_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_attribute_definitions
    ADD CONSTRAINT offering_attribute_definitions_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: offering_attribute_values offering_attribute_values_attribute_definition_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_attribute_values
    ADD CONSTRAINT offering_attribute_values_attribute_definition_id_fkey FOREIGN KEY (attribute_definition_id) REFERENCES public.offering_attribute_definitions(id) ON DELETE RESTRICT;


--
-- Name: offering_attribute_values offering_attribute_values_offering_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_attribute_values
    ADD CONSTRAINT offering_attribute_values_offering_id_fkey FOREIGN KEY (offering_id) REFERENCES public.offerings(id) ON DELETE RESTRICT;


--
-- Name: offering_attribute_values offering_attribute_values_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_attribute_values
    ADD CONSTRAINT offering_attribute_values_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: offering_categories offering_categories_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_categories
    ADD CONSTRAINT offering_categories_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: offering_prices offering_prices_offering_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_prices
    ADD CONSTRAINT offering_prices_offering_id_fkey FOREIGN KEY (offering_id) REFERENCES public.offerings(id) ON DELETE RESTRICT;


--
-- Name: offering_prices offering_prices_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offering_prices
    ADD CONSTRAINT offering_prices_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: offerings offerings_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offerings
    ADD CONSTRAINT offerings_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.offering_categories(id) ON DELETE RESTRICT;


--
-- Name: offerings offerings_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.offerings
    ADD CONSTRAINT offerings_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: opportunities opportunities_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.opportunities
    ADD CONSTRAINT opportunities_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE RESTRICT;


--
-- Name: opportunities opportunities_lead_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.opportunities
    ADD CONSTRAINT opportunities_lead_id_fkey FOREIGN KEY (lead_id) REFERENCES public.leads(id) ON DELETE RESTRICT;


--
-- Name: opportunities opportunities_offering_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.opportunities
    ADD CONSTRAINT opportunities_offering_id_fkey FOREIGN KEY (offering_id) REFERENCES public.offerings(id) ON DELETE RESTRICT;


--
-- Name: opportunities opportunities_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.opportunities
    ADD CONSTRAINT opportunities_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: opportunities opportunities_owner_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.opportunities
    ADD CONSTRAINT opportunities_owner_user_id_fkey FOREIGN KEY (owner_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: outbox_events outbox_events_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: role_permissions role_permissions_permission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.permissions(id) ON DELETE RESTRICT;


--
-- Name: role_permissions role_permissions_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE RESTRICT;


--
-- Name: roles roles_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: sales_assignments sales_assignments_assigned_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_assignments
    ADD CONSTRAINT sales_assignments_assigned_by_user_id_fkey FOREIGN KEY (assigned_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: sales_assignments sales_assignments_offering_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_assignments
    ADD CONSTRAINT sales_assignments_offering_id_fkey FOREIGN KEY (offering_id) REFERENCES public.offerings(id) ON DELETE RESTRICT;


--
-- Name: sales_assignments sales_assignments_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_assignments
    ADD CONSTRAINT sales_assignments_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: sales_assignments sales_assignments_sales_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales_assignments
    ADD CONSTRAINT sales_assignments_sales_user_id_fkey FOREIGN KEY (sales_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: source_record_links source_record_links_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_record_links
    ADD CONSTRAINT source_record_links_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: source_record_links source_record_links_source_system_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_record_links
    ADD CONSTRAINT source_record_links_source_system_id_fkey FOREIGN KEY (source_system_id) REFERENCES public.source_systems(id) ON DELETE RESTRICT;


--
-- Name: source_systems source_systems_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.source_systems
    ADD CONSTRAINT source_systems_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: support_attachments support_attachments_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_attachments
    ADD CONSTRAINT support_attachments_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: support_attachments support_attachments_support_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_attachments
    ADD CONSTRAINT support_attachments_support_request_id_fkey FOREIGN KEY (support_request_id) REFERENCES public.support_requests(id) ON DELETE RESTRICT;


--
-- Name: support_attachments support_attachments_support_response_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_attachments
    ADD CONSTRAINT support_attachments_support_response_id_fkey FOREIGN KEY (support_response_id) REFERENCES public.support_responses(id) ON DELETE RESTRICT;


--
-- Name: support_attachments support_attachments_uploaded_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_attachments
    ADD CONSTRAINT support_attachments_uploaded_by_user_id_fkey FOREIGN KEY (uploaded_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: support_requests support_requests_assigned_to_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_requests
    ADD CONSTRAINT support_requests_assigned_to_user_id_fkey FOREIGN KEY (assigned_to_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: support_requests support_requests_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_requests
    ADD CONSTRAINT support_requests_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customers(id) ON DELETE RESTRICT;


--
-- Name: support_requests support_requests_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_requests
    ADD CONSTRAINT support_requests_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: support_responses support_responses_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_responses
    ADD CONSTRAINT support_responses_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: support_responses support_responses_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_responses
    ADD CONSTRAINT support_responses_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: support_responses support_responses_support_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.support_responses
    ADD CONSTRAINT support_responses_support_request_id_fkey FOREIGN KEY (support_request_id) REFERENCES public.support_requests(id) ON DELETE RESTRICT;


--
-- Name: team_memberships team_memberships_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_memberships
    ADD CONSTRAINT team_memberships_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: team_memberships team_memberships_team_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_memberships
    ADD CONSTRAINT team_memberships_team_id_fkey FOREIGN KEY (team_id) REFERENCES public.teams(id) ON DELETE RESTRICT;


--
-- Name: team_memberships team_memberships_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_memberships
    ADD CONSTRAINT team_memberships_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: teams teams_department_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_department_id_fkey FOREIGN KEY (department_id) REFERENCES public.departments(id) ON DELETE RESTRICT;


--
-- Name: teams teams_manager_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_manager_user_id_fkey FOREIGN KEY (manager_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: teams teams_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teams
    ADD CONSTRAINT teams_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: user_role_grants user_role_grants_granted_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_grants
    ADD CONSTRAINT user_role_grants_granted_by_user_id_fkey FOREIGN KEY (granted_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: user_role_grants user_role_grants_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_grants
    ADD CONSTRAINT user_role_grants_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: user_role_grants user_role_grants_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_grants
    ADD CONSTRAINT user_role_grants_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE RESTRICT;


--
-- Name: user_role_grants user_role_grants_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role_grants
    ADD CONSTRAINT user_role_grants_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: users users_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: workflow_executions workflow_executions_created_task_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_executions
    ADD CONSTRAINT workflow_executions_created_task_id_fkey FOREIGN KEY (created_task_id) REFERENCES public.crm_tasks(id) ON DELETE RESTRICT;


--
-- Name: workflow_executions workflow_executions_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_executions
    ADD CONSTRAINT workflow_executions_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: workflow_executions workflow_executions_outbox_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workflow_executions
    ADD CONSTRAINT workflow_executions_outbox_event_id_fkey FOREIGN KEY (outbox_event_id) REFERENCES public.outbox_events(id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--



