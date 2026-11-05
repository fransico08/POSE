CREATE TABLE source_systems (id UUID PRIMARY KEY, organization_id UUID NOT NULL, code VARCHAR(80) NOT NULL, name VARCHAR(160) NOT NULL, source_type VARCHAR(30) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE TABLE source_record_links (id UUID PRIMARY KEY, organization_id UUID NOT NULL, source_system_id UUID NOT NULL, subject_type VARCHAR(20) NOT NULL, subject_id UUID NOT NULL, external_id VARCHAR(180) NOT NULL, source_entity_type VARCHAR(60) NOT NULL, status VARCHAR(20) NOT NULL, linked_at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE TABLE feedbacks (id UUID PRIMARY KEY, organization_id UUID NOT NULL, customer_id UUID, lead_id UUID, rating INTEGER, visibility VARCHAR(20) NOT NULL);
CREATE TABLE support_requests (id UUID PRIMARY KEY, organization_id UUID NOT NULL, customer_id UUID NOT NULL, assigned_to_user_id UUID NOT NULL, title VARCHAR(220) NOT NULL, category VARCHAR(80) NOT NULL, description TEXT NOT NULL, priority VARCHAR(20) NOT NULL, status VARCHAR(30) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL);
CREATE VIEW customer_360_summary AS
SELECT c.id customer_id, c.organization_id,
 (SELECT count(*) FROM source_record_links s WHERE s.subject_type='CUSTOMER' AND s.subject_id=c.id AND s.status='ACTIVE') source_count,
 (SELECT count(*) FROM opportunities o WHERE o.customer_id=c.id) opportunity_count,
 (SELECT count(*) FROM interactions i WHERE i.customer_id=c.id) interaction_count,
 (SELECT count(*) FROM feedbacks f WHERE f.customer_id=c.id) feedback_count,
 (SELECT count(*) FROM crm_tasks t WHERE t.customer_id=c.id AND t.status IN ('OPEN','IN_PROGRESS')) open_task_count,
 (SELECT count(*) FROM support_requests sr WHERE sr.customer_id=c.id AND sr.status NOT IN ('RESOLVED','CLOSED')) open_support_count,
 (SELECT max(i.occurred_at) FROM interactions i WHERE i.customer_id=c.id) last_interaction_at
FROM customers c;
