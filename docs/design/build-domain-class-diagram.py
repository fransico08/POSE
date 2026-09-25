"""Build the editable POSE CRM UML class diagram and review images.

Run from the repository root with the bundled Python runtime. The .drawio file
is the maintained artifact; PNGs are review previews generated from the same
declarative model.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from html import escape
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw, ImageFont


OUT = Path(__file__).resolve().parent
DRAWIO = OUT / "domain-class-diagram-v1.drawio"
FONT_PATH = Path("C:/Windows/Fonts/arial.ttf")
BOLD_PATH = Path("C:/Windows/Fonts/arialbd.ttf")


@dataclass
class UmlClass:
    key: str
    name: str
    x: int
    y: int
    w: int
    h: int
    attrs: list[str] = field(default_factory=list)
    ops: list[str] = field(default_factory=list)
    stereotype: str = "entity"
    external: bool = False


@dataclass
class Link:
    source: str
    target: str
    label: str
    kind: str = "association"  # association, composition, dependency


@dataclass
class Note:
    x: int
    y: int
    w: int
    h: int
    title: str
    body: str


@dataclass
class Page:
    id: str
    title: str
    subtitle: str
    width: int
    height: int
    classes: list[UmlClass]
    links: list[Link]
    notes: list[Note]


PAGES = [
    Page(
        "01-access", "01  Identity and organization",
        "UML class view - organization, membership and authorization",
        2030, 1230,
        [
            UmlClass("Organization", "Organization", 55, 150, 345, 245,
                     ["+ id: UUID", "+ name: String", "+ industry: String?", "+ status: OrganizationStatus", "+ createdAt: Instant"],
                     ["+ rename(name): void", "+ deactivate(): void"]),
            UmlClass("Department", "Department", 445, 150, 345, 240,
                     ["+ id: UUID", "+ code: String", "+ name: String", "+ status: DepartmentStatus"],
                     ["+ rename(name): void", "+ deactivate(): void"]),
            UmlClass("Team", "Team", 835, 150, 345, 270,
                     ["+ id: UUID", "+ code: String", "+ name: String", "+ status: TeamStatus", "+ managerId: UUID"],
                     ["+ assignManager(user): void", "+ changeStatus(status): void"]),
            UmlClass("TeamMembership", "TeamMembership", 1225, 150, 345, 235,
                     ["+ id: UUID", "+ joinedAt: Instant", "+ leftAt: Instant?", "+ status: MembershipStatus"],
                     ["+ end(at): void", "+ isActive(at): Boolean"]),
            UmlClass("User", "User", 1615, 150, 355, 310,
                     ["+ id: UUID", "+ email: String", "+ fullName: String", "+ status: UserStatus", "+ createdAt: Instant", "+ archivedAt: Instant?"],
                     ["+ activate(): void", "+ deactivate(): void", "+ hasRole(code): Boolean"]),
            UmlClass("Permission", "Permission", 55, 565, 345, 190,
                     ["+ code: String", "+ description: String"],
                     ["+ covers(action): Boolean"]),
            UmlClass("Role", "Role", 445, 565, 345, 220,
                     ["+ code: RoleCode", "+ displayName: String", "+ status: RoleStatus"],
                     ["+ grants(permission): Boolean"]),
            UmlClass("UserRoleGrant", "UserRoleGrant", 835, 565, 345, 240,
                     ["+ id: UUID", "+ grantedAt: Instant", "+ grantedById: UUID", "+ revokedAt: Instant?"],
                     ["+ revoke(at): void", "+ isActive(at): Boolean"]),
            UmlClass("Credential", "Credential", 1615, 565, 355, 210,
                     ["+ passwordHash: String", "+ passwordChangedAt: Instant", "+ failedLoginCount: Integer"],
                     ["+ replaceHash(hash): void", "+ resetFailures(): void"], stereotype="security entity"),
            UmlClass("RoleCode", "RoleCode", 55, 865, 460, 210,
                     ["ADMIN", "MANAGER", "SALES_REP", "CUSTOMER_CARE", "DATA_STAFF"],
                     [], stereotype="enumeration"),
        ],
        [
            Link("Organization", "Department", "1 / 0..* contains"),
            Link("Department", "Team", "1 / 0..* contains"),
            Link("Team", "TeamMembership", "1 / 0..* membership records", "composition"),
            Link("User", "TeamMembership", "1 / 0..* joins"),
            Link("Team", "User", "0..* / 1 managed by"),
            Link("Organization", "User", "1 / 0..* employs"),
            Link("Organization", "Role", "1 / 0..* role catalog"),
            Link("User", "UserRoleGrant", "1 / 0..* role grants", "composition"),
            Link("Role", "UserRoleGrant", "1 / 0..* granted role"),
            Link("Role", "Permission", "0..* / 0..* grants"),
            Link("User", "Credential", "1 / 1 credential", "composition"),
        ],
        [
            Note(565, 865, 1405, 210, "ACC-01 / ACC-02",
                 "All records and assigned roles stay inside one Organization. Effective permission is the union of active roles, followed by team and record scope. Only ADMIN can alter another user's roles; self elevation is forbidden."),
        ],
    ),
    Page(
        "02-offering", "02  Offering and sales scope",
        "UML class view - configurable products or services and effective assignments",
        2230, 1220,
        [
            UmlClass("CatalogOrganizationRef", "Organization", 55, 150, 285, 120,
                     ["+ id: UUID"], [], stereotype="reference", external=True),
            UmlClass("OfferingCategory", "OfferingCategory", 400, 150, 350, 255,
                     ["+ id: UUID", "+ code: String", "+ name: String", "+ status: CategoryStatus"],
                     ["+ rename(name): void", "+ deactivate(): void"]),
            UmlClass("Offering", "Offering", 815, 150, 410, 335,
                     ["+ id: UUID", "+ code: String", "+ name: String", "+ description: String?", "+ kind: OfferingKind", "+ unit: String?", "+ status: OfferingStatus"],
                     ["+ activate(): void", "+ deactivate(reason): void"]),
            UmlClass("OfferingAttributeDefinition", "OfferingAttributeDefinition", 1300, 150, 410, 300,
                     ["+ id: UUID", "+ key: String", "+ label: String", "+ valueType: AttributeType", "+ required: Boolean", "+ validationRule: String?"],
                     ["+ validate(value): ValidationResult"]),
            UmlClass("OfferingPrice", "OfferingPrice", 1785, 150, 380, 265,
                     ["+ amount: Decimal", "+ currency: CurrencyCode", "+ pricingUnit: String?", "+ effectiveFrom: Instant", "+ effectiveTo: Instant?"],
                     ["+ isEffective(at): Boolean"], stereotype="value object"),
            UmlClass("CatalogUserRef", "User", 55, 565, 285, 120,
                     ["+ id: UUID", "+ status: UserStatus"], [], stereotype="reference", external=True),
            UmlClass("SalesAssignment", "SalesAssignment", 815, 565, 410, 340,
                     ["+ id: UUID", "+ status: AssignmentStatus", "+ effectiveFrom: Instant", "+ effectiveTo: Instant?", "+ assignedById: UUID", "+ revokedReason: String?"],
                     ["+ isEffective(at): Boolean", "+ extend(to): void", "+ revoke(reason): void"]),
            UmlClass("OfferingAttributeValue", "OfferingAttributeValue", 1300, 565, 410, 225,
                     ["+ id: UUID", "+ value: TypedValue", "+ updatedAt: Instant"],
                     ["+ change(value): void"]),
            UmlClass("OfferingStatus", "OfferingStatus", 55, 870, 285, 165,
                     ["DRAFT", "ACTIVE", "INACTIVE"], [], stereotype="enumeration"),
            UmlClass("AssignmentStatus", "AssignmentStatus", 400, 870, 335, 165,
                     ["ACTIVE", "EXPIRED", "REVOKED"], [], stereotype="enumeration"),
        ],
        [
            Link("CatalogOrganizationRef", "OfferingCategory", "1 / 0..* owns"),
            Link("CatalogOrganizationRef", "Offering", "1 / 0..* owns"),
            Link("OfferingCategory", "Offering", "1 / 0..* classifies"),
            Link("OfferingCategory", "OfferingAttributeDefinition", "1 / 0..* defines"),
            Link("Offering", "OfferingAttributeValue", "1 / 0..* values", "composition"),
            Link("OfferingAttributeDefinition", "OfferingAttributeValue", "1 / 0..* constrains"),
            Link("Offering", "OfferingPrice", "1 / 0..* prices", "composition"),
            Link("Offering", "SalesAssignment", "1 / 0..* assigned"),
            Link("CatalogUserRef", "SalesAssignment", "1 / 0..* receives"),
        ],
        [
            Note(815, 965, 1350, 145, "CAT-01 / CAT-02",
                 "Only ACTIVE Offerings accept new Leads or Opportunities. SalesAssignment is effective when ACTIVE and inside its interval; Sale never creates or renews its own scope. Revoke/expiry requires reassignment of open work and an audit event."),
        ],
    ),
    Page(
        "03-sales", "03  Customer and opportunity lifecycle",
        "UML class view - polymorphic CRM subject, identity and source reconciliation",
        2540, 1320,
        [
            UmlClass("SalesUserRef", "User", 55, 145, 300, 125,
                     ["+ id: UUID", "+ status: UserStatus"], [], stereotype="reference", external=True),
            UmlClass("SalesOfferingRef", "Offering", 425, 145, 305, 125,
                     ["+ id: UUID", "+ status: OfferingStatus"], [], stereotype="reference", external=True),
            UmlClass("CrmSubject", "CrmSubject", 800, 145, 365, 205,
                     [], ["+ getId(): UUID", "+ getOrganizationId(): UUID", "+ getOwnerId(): UUID", "+ changeOwner(user): void", "+ archive(): void"], stereotype="interface"),
            UmlClass("ContactIdentity", "ContactIdentity", 1240, 145, 370, 240,
                     ["+ normalizedEmail: String?", "+ normalizedPhone: String?", "+ displayName: String"],
                     ["+ matchesExactly(other): Boolean", "+ normalize(): ContactIdentity"], stereotype="value object"),
            UmlClass("SalesSourceRef", "SourceSystem", 1690, 145, 320, 125,
                     ["+ id: UUID", "+ code: String"], [], stereotype="reference", external=True),
            UmlClass("LeadStatus", "LeadStatus", 2070, 145, 360, 200,
                     ["NEW", "QUALIFIED", "CONVERTED", "DISQUALIFIED"], [], stereotype="enumeration"),
            UmlClass("Customer", "Customer", 55, 460, 395, 300,
                     ["+ id: UUID", "+ organizationId: UUID", "+ ownerId: UUID", "+ status: CustomerStatus", "+ createdAt: Instant", "+ archivedAt: Instant?"],
                     ["+ updateIdentity(identity): void", "+ archive(): void"]),
            UmlClass("Lead", "Lead", 535, 460, 395, 330,
                     ["+ id: UUID", "+ organizationId: UUID", "+ ownerId: UUID", "+ status: LeadStatus", "+ qualifiedAt: Instant?", "+ convertedAt: Instant?"],
                     ["+ qualify(): void", "+ disqualify(reason): void", "+ markConverted(customer): void"]),
            UmlClass("Opportunity", "Opportunity", 1015, 460, 440, 360,
                     ["+ id: UUID", "+ stage: OpportunityStage", "+ expectedValue: Money?", "+ closingReason: String?", "+ closedAt: Instant?", "+ createdAt: Instant"],
                     ["+ advance(next): void", "+ close(result, reason): void", "+ reopen(): void"]),
            UmlClass("SourceRecordLink", "SourceRecordLink", 1540, 460, 400, 285,
                     ["+ id: UUID", "+ externalId: String", "+ sourceEntityType: String", "+ linkedAt: Instant", "+ status: LinkStatus"],
                     ["+ rebind(subject): void", "+ archive(): void"]),
            UmlClass("DuplicateReviewCase", "DuplicateReviewCase", 2020, 460, 420, 285,
                     ["+ id: UUID", "+ matchReason: String", "+ status: ReviewStatus", "+ candidateRef: String", "+ reviewedById: UUID?", "+ reviewedAt: Instant?"],
                     ["+ confirmMatch(): void", "+ rejectMatch(): void"]),
            UmlClass("OpportunityStage", "OpportunityStage", 55, 900, 395, 205,
                     ["NEW", "QUALIFIED", "PROPOSAL", "WON", "LOST"], [], stereotype="enumeration"),
        ],
        [
            Link("Customer", "CrmSubject", "realizes", "realization"),
            Link("Lead", "CrmSubject", "realizes", "realization"),
            Link("CrmSubject", "SalesUserRef", "0..* / 1 primary owner"),
            Link("Customer", "ContactIdentity", "1 / 1 identity", "composition"),
            Link("Lead", "ContactIdentity", "1 / 1 identity", "composition"),
            Link("SalesOfferingRef", "Lead", "1 / 0..* interest"),
            Link("SalesOfferingRef", "Opportunity", "1 / 0..* concerns"),
            Link("CrmSubject", "Opportunity", "1 / 0..* subject"),
            Link("SalesUserRef", "Opportunity", "1 / 0..* owner"),
            Link("Lead", "Customer", "0..* / 0..1 converts to"),
            Link("SalesSourceRef", "SourceRecordLink", "1 / 0..* source"),
            Link("CrmSubject", "SourceRecordLink", "1 / 0..* links"),
            Link("CrmSubject", "DuplicateReviewCase", "1 / 0..* review cases"),
        ],
        [
            Note(535, 900, 910, 205, "CRM-01 / CRM-02",
                 "CrmSubject is an interface, not a table. Every Opportunity has exactly one Customer or Lead subject, one Offering and one owner Sale with effective assignment. WON/LOST require closing reason; only Manager reopens to QUALIFIED."),
            Note(1540, 860, 900, 245, "CRM-03 / CRM-04",
                 "Exact normalized email or phone match blocks silent duplicate creation. Suspected match becomes DuplicateReviewCase. SourceRecordLink keeps multiple external identities; (organization, source, type, externalId) is unique. Conversion rebinds activity and source links."),
        ],
    ),
    Page(
        "04-care", "04  Interaction, feedback and follow-up",
        "UML class view - activity ownership, visibility and Customer 360 read model",
        2110, 1160,
        [
            UmlClass("CareSubjectRef", "CrmSubject", 55, 145, 350, 140,
                     [], ["+ getId(): UUID", "+ getOwnerId(): UUID"], stereotype="reference", external=True),
            UmlClass("CareOpportunityRef", "Opportunity", 470, 145, 350, 120,
                     ["+ id: UUID", "+ stage: OpportunityStage"], [], stereotype="reference", external=True),
            UmlClass("CareUserRef", "User", 885, 145, 350, 120,
                     ["+ id: UUID", "+ status: UserStatus"], [], stereotype="reference", external=True),
            UmlClass("CareCustomerRef", "Customer", 1500, 145, 350, 120,
                     ["+ id: UUID"], [], stereotype="reference", external=True),
            UmlClass("Interaction", "Interaction", 55, 445, 420, 350,
                     ["+ id: UUID", "+ type: InteractionType", "+ channel: Channel", "+ subject: String", "+ content: String", "+ outcome: String?", "+ visibility: NoteVisibility", "+ occurredAt: Instant"],
                     ["+ recordOutcome(outcome): void"]),
            UmlClass("Feedback", "Feedback", 535, 445, 420, 310,
                     ["+ id: UUID", "+ category: FeedbackCategory", "+ content: String", "+ rating: Integer?", "+ visibility: NoteVisibility", "+ receivedAt: Instant"],
                     ["+ classify(category): void"]),
            UmlClass("CrmTask", "CrmTask", 1015, 445, 440, 365,
                     ["+ id: UUID", "+ title: String", "+ priority: TaskPriority", "+ status: TaskStatus", "+ dueAt: Instant", "+ result: String?", "+ origin: TaskOrigin", "+ idempotencyKey: String?"],
                     ["+ assign(user): void", "+ start(): void", "+ complete(result): void", "+ cancel(reason): void"]),
            UmlClass("Customer360Summary", "Customer360Summary", 1515, 445, 490, 305,
                     ["+ customerId: UUID", "+ ownerId: UUID", "+ sourceCount: Integer", "+ opportunityCount: Integer", "+ interactionCount: Integer", "+ feedbackCount: Integer", "+ openTaskCount: Integer", "+ lastInteractionAt: Instant?", "+ refreshedAt: Instant"],
                     [], stereotype="read model"),
            UmlClass("TaskStatus", "TaskStatus", 55, 865, 420, 180,
                     ["OPEN", "IN_PROGRESS", "DONE", "CANCELLED"], [], stereotype="enumeration"),
            UmlClass("NoteVisibility", "NoteVisibility", 535, 865, 420, 145,
                     ["CARE_VISIBLE", "SALES_ONLY"], [], stereotype="enumeration"),
        ],
        [
            Link("CareSubjectRef", "Interaction", "1 / 0..* interactions"),
            Link("CareSubjectRef", "Feedback", "1 / 0..* feedback"),
            Link("CareSubjectRef", "CrmTask", "1 / 0..* tasks"),
            Link("CareOpportunityRef", "Interaction", "0..1 / 0..* context"),
            Link("CareOpportunityRef", "CrmTask", "0..1 / 0..* context"),
            Link("CareUserRef", "Interaction", "1 / 0..* author"),
            Link("CareUserRef", "Feedback", "1 / 0..* recorder"),
            Link("CareUserRef", "CrmTask", "1 / 0..* assignee"),
            Link("CareCustomerRef", "Customer360Summary", "1 / 0..1 projection", "dependency"),
        ],
        [
            Note(1015, 865, 990, 180, "CARE-01 / CARE-02",
                 "Every activity has exactly one CrmSubject. Opportunity is optional context. SALES_ONLY content is excluded from Customer Care responses. Workflow task creation uses one idempotency key; Customer360Summary is read-only and traceable to source records."),
        ],
    ),
    Page(
        "05-integration", "05  Import, audit and workflow",
        "UML class view - data decisions, event delivery and application controls",
        2460, 1510,
        [
            UmlClass("ImportSourceRef", "SourceSystem", 55, 145, 345, 120,
                     ["+ id: UUID", "+ code: String"], [], stereotype="reference", external=True),
            UmlClass("ImportJob", "ImportJob", 470, 145, 395, 385,
                     ["+ id: UUID", "+ status: ImportStatus", "+ originalFileRef: String", "+ submittedById: UUID", "+ idempotencyKey: String", "+ retryCount: Integer", "+ submittedAt: Instant"],
                     ["+ beginValidation(): void", "+ requireReview(): void", "+ markCommitted(): void", "+ fail(reason): void", "+ retryFailed(): void"]),
            UmlClass("ImportRow", "ImportRow", 935, 145, 355, 305,
                     ["+ id: UUID", "+ rowNumber: Integer", "+ status: RowStatus", "+ errorCodes: Set<String>", "+ normalizedPayloadRef: String?"],
                     ["+ markValid(): void", "+ markInvalid(errors): void"]),
            UmlClass("ImportDecision", "ImportDecision", 1360, 145, 355, 265,
                     ["+ id: UUID", "+ decision: ApprovalDecision", "+ decidedById: UUID", "+ decidedAt: Instant", "+ reason: String?"],
                     [], stereotype="immutable entity"),
            UmlClass("DataValidationGateway", "DataValidationGateway", 1785, 145, 355, 215,
                     [], ["+ validate(job): ValidationReport"], stereotype="interface"),
            UmlClass("AuditLog", "AuditLog", 55, 635, 345, 280,
                     ["+ id: UUID", "+ actorId: UUID", "+ action: String", "+ entityType: String", "+ entityId: UUID", "+ safeMetadata: Map", "+ occurredAt: Instant"],
                     [], stereotype="append-only"),
            UmlClass("OutboxEvent", "OutboxEvent", 470, 635, 395, 280,
                     ["+ id: UUID", "+ aggregateRef: String", "+ eventType: String", "+ payloadRef: String", "+ deliveryStatus: DeliveryStatus", "+ occurredAt: Instant"],
                     ["+ markPublished(): void"], stereotype="event record"),
            UmlClass("WorkflowExecution", "WorkflowExecution", 935, 635, 355, 310,
                     ["+ id: UUID", "+ idempotencyKey: String", "+ status: WorkflowStatus", "+ retryCount: Integer", "+ lastError: String?"],
                     ["+ start(): void", "+ fail(error): void", "+ finish(): void"]),
            UmlClass("ImportApplicationService", "ImportApplicationService", 1360, 635, 410, 255,
                     [], ["+ receive(file, source): ImportJob", "+ validate(job): ValidationReport", "+ decide(job, actor): ImportDecision", "+ commitApproved(job): void"], stereotype="control"),
            UmlClass("WorkflowCommandService", "WorkflowCommandService", 1840, 635, 485, 255,
                     [], ["+ handle(event, key): WorkflowExecution", "+ createTaskOnce(command): CrmTask", "+ retryFailed(execution): void"], stereotype="control"),
            UmlClass("AuthorizationPolicy", "AuthorizationPolicy", 1360, 1010, 410, 225,
                     [], ["+ checkOrganization(actor, record): void", "+ checkPermission(actor, action): void", "+ checkRecordScope(actor, record): void"], stereotype="control"),
            UmlClass("Customer360QueryService", "Customer360QueryService", 1840, 1010, 485, 225,
                     [], ["+ getProfile(customerId, actor): Customer360Summary", "+ verifyScope(actor, customer): void", "+ aggregateSources(customer): Summary"], stereotype="control"),
            UmlClass("ImportStatus", "ImportStatus", 55, 1010, 345, 220,
                     ["RECEIVED", "VALIDATING", "REVIEW_REQUIRED", "COMMITTED", "FAILED"], [], stereotype="enumeration"),
            UmlClass("WorkflowStatus", "WorkflowStatus", 470, 1010, 395, 190,
                     ["PENDING", "RUNNING", "DONE", "FAILED"], [], stereotype="enumeration"),
        ],
        [
            Link("ImportSourceRef", "ImportJob", "1 / 0..* source"),
            Link("ImportJob", "ImportRow", "1 / 0..* rows", "composition"),
            Link("ImportJob", "ImportDecision", "1 / 0..1 decision", "composition"),
            Link("ImportApplicationService", "ImportJob", "commands", "dependency"),
            Link("ImportApplicationService", "DataValidationGateway", "validates through", "dependency"),
            Link("OutboxEvent", "WorkflowExecution", "1 / 0..* triggers"),
            Link("WorkflowCommandService", "WorkflowExecution", "records", "dependency"),
            Link("ImportApplicationService", "AuthorizationPolicy", "checks approval", "dependency"),
            Link("Customer360QueryService", "AuthorizationPolicy", "checks scope", "dependency"),
        ],
        [
            Note(55, 1290, 2270, 125, "INT-01 / INT-02",
                 "Data Service only validates; Backend approves and commits. Only Manager/Admin may approve import. Data Staff retries FAILED jobs using the same key. Audit entries and event payloads are immutable; workflow and delivery statuses change. n8n never writes PostgreSQL."),
        ],
    ),
    Page(
        "06-support", "06  Support request and customer handover",
        "UML class view - escalated support history and manager-approved ownership transfer",
        2230, 1200,
        [
            UmlClass("SupportCustomerRef", "Customer", 55, 145, 320, 120,
                     ["+ id: UUID", "+ ownerId: UUID"], [], stereotype="reference", external=True),
            UmlClass("SupportUserRef", "User", 420, 145, 320, 120,
                     ["+ id: UUID", "+ status: UserStatus"], [], stereotype="reference", external=True),
            UmlClass("HandoverCustomerRef", "Customer", 1180, 145, 320, 120,
                     ["+ id: UUID", "+ ownerId: UUID"], [], stereotype="reference", external=True),
            UmlClass("HandoverUserRef", "User", 1560, 145, 320, 120,
                     ["+ id: UUID", "+ status: UserStatus"], [], stereotype="reference", external=True),
            UmlClass("SupportRequest", "SupportRequest", 55, 380, 470, 375,
                     ["+ id: UUID", "+ title: String", "+ category: String", "+ description: String", "+ priority: SupportPriority", "+ status: SupportStatus", "+ escalationReason: String?", "+ resolution: String?", "+ createdAt: Instant"],
                     ["+ assign(user): void", "+ respond(content): SupportResponse", "+ escalate(manager, reason): void", "+ resolve(result): void", "+ close(): void"]),
            UmlClass("SupportResponse", "SupportResponse", 600, 380, 400, 190,
                     ["+ id: UUID", "+ content: String", "+ statusAfter: SupportStatus?", "+ createdById: UUID", "+ createdAt: Instant"],
                     [], stereotype="immutable entity"),
            UmlClass("SupportAttachment", "SupportAttachment", 600, 620, 400, 225,
                     ["+ id: UUID", "+ fileName: String", "+ fileType: String", "+ fileSize: Long", "+ storageRef: String", "+ uploadedById: UUID", "+ uploadedAt: Instant"],
                     []),
            UmlClass("HandoverRequest", "HandoverRequest", 1180, 380, 470, 335,
                     ["+ id: UUID", "+ type: HandoverType", "+ reason: String", "+ status: HandoverStatus", "+ requestedById: UUID", "+ targetUserId: UUID?", "+ reviewedById: UUID?", "+ reviewedAt: Instant?", "+ rejectionReason: String?"],
                     ["+ submit(): void", "+ approve(target): void", "+ reject(reason): void"]),
            UmlClass("HandoverType", "HandoverType", 1720, 380, 420, 130,
                     ["SALES", "CARE"], [], stereotype="enumeration"),
            UmlClass("HandoverStatus", "HandoverStatus", 1720, 560, 420, 150,
                     ["PENDING", "APPROVED", "REJECTED"], [], stereotype="enumeration"),
            UmlClass("SupportStatus", "SupportStatus", 55, 900, 420, 185,
                     ["OPEN", "IN_PROGRESS", "ESCALATED", "RESOLVED", "CLOSED"], [], stereotype="enumeration"),
            UmlClass("SupportPriority", "SupportPriority", 530, 900, 330, 165,
                     ["LOW", "MEDIUM", "HIGH", "URGENT"], [], stereotype="enumeration"),
        ],
        [
            Link("SupportCustomerRef", "SupportRequest", "1 / 0..* requests"),
            Link("SupportUserRef", "SupportRequest", "1 / 0..* assignee"),
            Link("SupportRequest", "SupportResponse", "1 / 0..* responses", "composition"),
            Link("SupportRequest", "SupportAttachment", "1 / 0..* attachments", "composition"),
            Link("HandoverCustomerRef", "HandoverRequest", "1 / 0..* handovers"),
            Link("HandoverUserRef", "HandoverRequest", "1 / 0..* requester"),
        ],
        [
            Note(940, 900, 1240, 200, "SUP-01 / HND-01",
                 "Customer Care works only on assigned requests; ESCALATED goes to a Manager who records a decision and returns it. Responses are kept as history. A Sale requests handover only for an owned Customer; one PENDING request per Customer; nothing changes until a Manager approves. SALES type: target is a Sale with an ACTIVE assignment for the related Offering. CARE type: target is Customer Care and approval creates a care assignment. All transitions are audited."),
        ],
    ),
]


def html_label(node: UmlClass) -> str:
    color = "#525F70" if node.external else "#162435"
    header = f'<div style="padding:8px 10px;background:#E9EEF4;color:{color};font-size:17px"><b>{escape(node.name)}</b></div>'
    stereotype = f'<div style="padding:3px 10px;color:#52677D;font-size:12px">&lt;&lt;{escape(node.stereotype)}&gt;&gt;</div>'
    attrs = '<div style="padding:6px 10px;font-family:monospace;font-size:12px;border-top:1px solid #B7C4D1">' + '<br>'.join(escape('-' + s[1:] if s.startswith('+') else s) for s in node.attrs) + '</div>' if node.attrs else ''
    ops = '<div style="padding:6px 10px;font-family:monospace;font-size:12px;border-top:1px solid #B7C4D1">' + '<br>'.join(escape(s) for s in node.ops) + '</div>' if node.ops else ''
    return header + stereotype + attrs + ops


def make_drawio() -> None:
    root = ET.Element("mxfile", {"host": "app.diagrams.net", "modified": "2026-09-19T00:00:00.000Z", "agent": "POSE", "version": "24.7.17", "type": "device"})
    for page in PAGES:
        diagram = ET.SubElement(root, "diagram", {"id": page.id, "name": page.title})
        graph = ET.SubElement(diagram, "mxGraphModel", {"dx": "1600", "dy": "900", "grid": "1", "gridSize": "10", "guides": "1", "tooltips": "1", "connect": "1", "arrows": "1", "fold": "1", "page": "1", "pageScale": "1", "pageWidth": str(page.width), "pageHeight": str(page.height), "math": "0", "shadow": "0"})
        cells = ET.SubElement(graph, "root")
        ET.SubElement(cells, "mxCell", {"id": "0"})
        ET.SubElement(cells, "mxCell", {"id": "1", "parent": "0"})

        def vertex(cell_id: str, value: str, style: str, x: int, y: int, w: int, h: int) -> None:
            cell = ET.SubElement(cells, "mxCell", {"id": cell_id, "value": value, "style": style, "vertex": "1", "parent": "1"})
            ET.SubElement(cell, "mxGeometry", {"x": str(x), "y": str(y), "width": str(w), "height": str(h), "as": "geometry"})

        vertex("page-title", escape(page.title), "text;html=1;align=left;verticalAlign=middle;whiteSpace=wrap;fontSize=26;fontStyle=1;fontColor=#162435;", 70, 35, page.width - 140, 50)
        vertex("page-subtitle", escape(page.subtitle), "text;html=1;align=left;verticalAlign=middle;whiteSpace=wrap;fontSize=16;fontColor=#52677D;", 70, 88, page.width - 140, 38)
        vertex("legend", "Association: solid  |  Composition: filled diamond  |  Realization: dashed hollow triangle  |  Dependency: dashed arrow  |  Multiplicity at both ends", "text;html=1;align=left;verticalAlign=middle;whiteSpace=wrap;fontSize=13;fontColor=#52677D;", 70, page.height - 62, page.width - 140, 35)

        # Draw edges before nodes so associations remain behind the UML boxes.
        for idx, link in enumerate(page.links):
            style = "edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;strokeColor=#74889C;strokeWidth=1.4;endArrow=none;labelBackgroundColor=#FFFFFF;fontSize=12;fontColor=#33495E;"
            if link.kind == "composition":
                style += "startArrow=diamond;startFill=1;startSize=15;"
            if link.kind == "dependency":
                style += "dashed=1;dashPattern=5 4;endArrow=open;endFill=0;"
            if link.kind == "realization":
                style += "dashed=1;dashPattern=5 4;endArrow=block;endFill=0;endSize=16;"
            source_mult = target_mult = None
            role = link.label
            if " / " in link.label:
                source_mult, remaining = link.label.split(" / ", 1)
                target_mult, role = remaining.split(" ", 1)
            edge = ET.SubElement(cells, "mxCell", {"id": f"edge-{idx}", "value": escape(role), "style": style, "edge": "1", "parent": "1", "source": link.source, "target": link.target})
            ET.SubElement(edge, "mxGeometry", {"relative": "1", "as": "geometry"})
            for end, mult, x_pos in (("source", source_mult, "-1"), ("target", target_mult, "1")):
                if mult is None:
                    continue
                label = ET.SubElement(cells, "mxCell", {"id": f"edge-{idx}-{end}-mult", "value": mult, "style": "edgeLabel;html=1;align=center;verticalAlign=middle;resizable=0;points=[];fontSize=12;fontColor=#24384C;labelBackgroundColor=#FFFFFF;", "vertex": "1", "connectable": "0", "parent": f"edge-{idx}"})
                geometry = ET.SubElement(label, "mxGeometry", {"x": x_pos, "y": "-1", "relative": "1", "as": "geometry"})
                ET.SubElement(geometry, "mxPoint", {"x": "12" if end == "source" else "-12", "y": "-12", "as": "offset"})

        for node in page.classes:
            fill = "#F6F8FB" if node.external else ("#F4F8FA" if node.stereotype == "control" else "#FFFFFF")
            style = f"rounded=0;whiteSpace=wrap;html=1;align=left;verticalAlign=top;spacing=0;fillColor={fill};strokeColor=#68809A;strokeWidth=1.7;shadow=0;overflow=fill;"
            vertex(node.key, html_label(node), style, node.x, node.y, node.w, node.h)

        for idx, note in enumerate(page.notes):
            text = f'<b>{escape(note.title)}</b><br>{escape(note.body)}'
            vertex(f"note-{idx}", text, "rounded=1;arcSize=7;whiteSpace=wrap;html=1;align=left;verticalAlign=top;spacing=12;fillColor=#F3F6FA;strokeColor=#C7D2DE;fontSize=14;fontColor=#24384C;", note.x, note.y, note.w, note.h)

    ET.indent(root, space="  ")
    ET.ElementTree(root).write(DRAWIO, encoding="utf-8", xml_declaration=True)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(BOLD_PATH if bold else FONT_PATH), size)


def wrap(draw: ImageDraw.ImageDraw, value: str, max_width: int, text_font: ImageFont.FreeTypeFont) -> list[str]:
    words = value.split()
    lines: list[str] = []
    current = ""
    for word in words:
        candidate = (current + " " + word).strip()
        if current and draw.textlength(candidate, font=text_font) > max_width:
            lines.append(current)
            current = word
        else:
            current = candidate
    if current:
        lines.append(current)
    return lines


def validate_pages() -> None:
    measure = ImageDraw.Draw(Image.new("RGB", (10, 10)))
    body, heading = font(13), font(17, True)
    ids: set[str] = set()
    for page in PAGES:
        assert page.id not in ids, f"duplicate page: {page.id}"
        ids.add(page.id)
        nodes = {node.key: node for node in page.classes}
        assert len(nodes) == len(page.classes), f"duplicate class key on {page.id}"
        for node in page.classes:
            assert 0 < node.x and 0 < node.y and node.x + node.w < page.width and node.y + node.h < page.height, (page.id, node.key, "outside page")
            assert measure.textlength(node.name, font=heading) <= node.w - 24, (page.id, node.key, "name too wide")
            for line in node.attrs + node.ops:
                assert measure.textlength(line, font=body) <= node.w - 24, (page.id, node.key, line, "text too wide")
            needed_height = 66 + 20 * len(node.attrs) + (13 + 20 * len(node.ops) if node.ops else 0)
            assert needed_height <= node.h - 8, (page.id, node.key, "text too tall")
        for left_index, left in enumerate(page.classes):
            for right in page.classes[left_index + 1:]:
                overlaps = left.x < right.x + right.w and right.x < left.x + left.w and left.y < right.y + right.h and right.y < left.y + left.h
                assert not overlaps, (page.id, left.key, right.key, "overlapping boxes")
        for link in page.links:
            assert link.source in nodes and link.target in nodes, (page.id, link, "missing endpoint")
            assert link.kind in {"association", "composition", "dependency", "realization"}, (page.id, link, "unknown relation")
        for note in page.notes:
            assert note.x + note.w < page.width and note.y + note.h < page.height, (page.id, note.title, "note outside page")


def draw_preview(page: Page) -> None:
    img = Image.new("RGB", (page.width, page.height), "#FFFFFF")
    d = ImageDraw.Draw(img)
    title_font, sub_font = font(28, True), font(16)
    body_font, header_font, note_font, edge_font = font(13), font(17, True), font(14), font(11)
    d.text((70, 35), page.title, font=title_font, fill="#162435")
    d.text((70, 89), page.subtitle, font=sub_font, fill="#52677D")
    classes = {n.key: n for n in page.classes}

    # The preview uses fixed orthogonal routes for rapid review. The draw.io
    # file contains native connectors and can be rerouted by hand.
    for link in page.links:
        a, b = classes[link.source], classes[link.target]
        ax, ay = a.x + a.w / 2, a.y + a.h / 2
        bx, by = b.x + b.w / 2, b.y + b.h / 2
        if abs(bx - ax) > abs(by - ay):
            start = (a.x + (a.w if bx > ax else 0), ay)
            end = (b.x + (0 if bx > ax else b.w), by)
            mid = (start[0] + end[0]) / 2
            points = [start, (mid, ay), (mid, by), end]
        else:
            start = (ax, a.y + (a.h if by > ay else 0))
            end = (bx, b.y + (0 if by > ay else b.h))
            mid = (start[1] + end[1]) / 2
            points = [start, (ax, mid), (bx, mid), end]
        d.line(points, fill="#A0B0BF", width=2, joint="curve")
        relation = link.label
        source_mult = target_mult = None
        if " / " in relation:
            source_mult, rest = relation.split(" / ", 1)
            target_mult, relation = rest.split(" ", 1)
        segments = list(zip(points, points[1:]))
        best = max(segments, key=lambda s: abs(s[1][0] - s[0][0]) + abs(s[1][1] - s[0][1]))
        label_x = (best[0][0] + best[1][0]) / 2
        label_y = (best[0][1] + best[1][1]) / 2
        label_w = d.textlength(relation, font=edge_font)
        if label_w + 16 < max(abs(best[1][0] - best[0][0]), abs(best[1][1] - best[0][1])):
            d.rectangle((label_x - label_w / 2 - 5, label_y - 12, label_x + label_w / 2 + 5, label_y + 4), fill="#FFFFFF")
            d.text((label_x - label_w / 2, label_y - 11), relation, font=edge_font, fill="#4A6075")
        if source_mult is not None:
            d.text((start[0] + 5, start[1] + 5), source_mult, font=edge_font, fill="#33495E", stroke_width=2, stroke_fill="#FFFFFF")
            d.text((end[0] + 5, end[1] - 18), target_mult, font=edge_font, fill="#33495E", stroke_width=2, stroke_fill="#FFFFFF")

    for node in page.classes:
        x, y, w, h = node.x, node.y, node.w, node.h
        fill = "#F6F8FB" if node.external else ("#F4F8FA" if node.stereotype == "control" else "#FFFFFF")
        d.rectangle((x, y, x + w, y + h), fill=fill, outline="#68809A", width=2)
        d.rectangle((x, y, x + w, y + 39), fill="#E9EEF4")
        d.text((x + 12, y + 9), node.name, font=header_font, fill="#162435")
        d.text((x + 12, y + 44), f"<<{node.stereotype}>>", font=font(12), fill="#52677D")
        row_y = y + 66
        for item in node.attrs:
            d.text((x + 12, row_y), '-' + item[1:] if item.startswith('+') else item, font=body_font, fill="#263849")
            row_y += 20
        if node.ops:
            d.line((x, row_y + 5, x + w, row_y + 5), fill="#B7C4D1", width=1)
            row_y += 13
            for item in node.ops:
                d.text((x + 12, row_y), item, font=body_font, fill="#263849")
                row_y += 20

    for note in page.notes:
        x, y, w, h = note.x, note.y, note.w, note.h
        d.rounded_rectangle((x, y, x + w, y + h), radius=10, fill="#F3F6FA", outline="#C7D2DE", width=2)
        d.text((x + 13, y + 12), note.title, font=header_font, fill="#24384C")
        line_y = y + 43
        for line in wrap(d, note.body, w - 26, note_font):
            d.text((x + 13, line_y), line, font=note_font, fill="#24384C")
            line_y += 20

    d.text((70, page.height - 53), "Association: solid   |   Composition: filled diamond   |   Realization: hollow triangle   |   Dependency: dashed arrow   |   Multiplicities at both ends", font=sub_font, fill="#52677D")
    img.save(OUT / f"domain-class-diagram-v1-preview-{page.id}.png")


if __name__ == "__main__":
    validate_pages()
    make_drawio()
    for diagram_page in PAGES:
        draw_preview(diagram_page)
    print(DRAWIO)
