"use client";

import { useMemo, useState, type FormEvent } from "react";
import { TrashIcon } from "@heroicons/react/24/outline";
import { Alert } from "@/components/ui/Alert";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { DateInput } from "@/components/ui/DateInput";
import { EmptyState } from "@/components/ui/EmptyState";
import { useModal } from "@/components/ui/use-modal";
import { formatIsoDateAsDdMmYyyy, parseDdMmYyyy } from "@/lib/date-format";
import type { OpportunityCustomer, OpportunityLine, OpportunityOffering, OpportunityRecord, OpportunityStage } from "./types";
import { allowedNextStages, expectedValue, formatVnd, opportunityStages, stageLabels } from "./types";

type DraftLine = { key: string; offeringId: string; quantity: string; unitPrice: string; discountPercent: string };
type Draft = { title: string; customerId: string; expectedCloseDate: string; stage: OpportunityStage; notes: string; closingReason: string; actualValue: string; actualCloseDate: string; lines: DraftLine[] };
type DraftScalarField = Exclude<keyof Draft, "lines">;
type Props = { initialOpportunities: OpportunityRecord[]; customers: OpportunityCustomer[]; offerings: OpportunityOffering[]; preview?: boolean; canWrite?: boolean };

const todayIso = () => { const date = new Date(); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`; };
const idFor = () => `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
const lineDraft = (offering?: OpportunityOffering): DraftLine => ({ key: idFor(), offeringId: offering?.id ?? "", quantity: "1", unitPrice: offering?.referencePrice.toString() ?? "", discountPercent: "0" });
const blankDraft = (offering?: OpportunityOffering): Draft => ({ title: "", customerId: "", expectedCloseDate: "", stage: "NEW", notes: "", closingReason: "", actualValue: "", actualCloseDate: "", lines: [lineDraft(offering)] });
const fromRecord = (record: OpportunityRecord): Draft => ({ title: record.title, customerId: record.customerId, expectedCloseDate: formatIsoDateAsDdMmYyyy(record.expectedCloseDate), stage: record.stage, notes: record.notes, closingReason: record.closingReason ?? "", actualValue: record.actualValue === undefined ? "" : String(record.actualValue), actualCloseDate: record.actualCloseDate ? formatIsoDateAsDdMmYyyy(record.actualCloseDate) : "", lines: record.lines.map(line => ({ key: line.id || idFor(), offeringId: line.offeringId, quantity: String(line.quantity), unitPrice: String(line.unitPrice), discountPercent: String(line.discountPercent) })) });
const lineIsValid = (line: DraftLine) => {
  const quantity = Number(line.quantity), unitPrice = Number(line.unitPrice), discount = Number(line.discountPercent);
  return Boolean(line.offeringId && line.quantity.trim() && Number.isInteger(quantity) && quantity > 0
    && line.unitPrice.trim() && Number.isFinite(unitPrice) && unitPrice >= 0
    && line.discountPercent.trim() && Number.isFinite(discount) && discount >= 0 && discount <= 100);
};
const dateLabel = formatIsoDateAsDdMmYyyy;
const statusTone = (stage: OpportunityStage): "info" | "success" | "warning" => stage === "WON" ? "success" : stage === "LOST" ? "warning" : "info";

export function OpportunityPipeline({ initialOpportunities, customers, offerings, preview = false, canWrite = true }: Props) {
  const [opportunities, setOpportunities] = useState(initialOpportunities);
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<OpportunityRecord | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [notice, setNotice] = useState("");
  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase("vi");
    return opportunities.filter(opportunity => !query || [opportunity.title, opportunity.customerName, ...opportunity.lines.map(line => line.offeringName)].some(value => value.toLocaleLowerCase("vi").includes(query)));
  }, [opportunities, search]);

  const openCreate = () => { setEditing(null); setNotice(""); setDialogOpen(true); };
  const openEdit = (record: OpportunityRecord) => { setEditing(record); setNotice(""); setDialogOpen(true); };
  const save = (draft: Draft) => {
    const customer = customers.find(item => item.id === draft.customerId);
    if (!customer) return;
    const lines: OpportunityLine[] = draft.lines.map(line => {
      const offering = offerings.find(item => item.id === line.offeringId)!;
      return { id: line.key, offeringId: offering.id, offeringName: offering.name, quantity: Number(line.quantity), unitPrice: Number(line.unitPrice), discountPercent: Number(line.discountPercent) };
    });
    const record: OpportunityRecord = {
      id: editing?.id ?? `preview-${idFor()}`, title: draft.title.trim(), customerId: customer.id, customerName: customer.name,
      stage: draft.stage, expectedCloseDate: parseDdMmYyyy(draft.expectedCloseDate)!, expectedValue: expectedValue(lines), notes: draft.notes.trim(),
      ...(draft.stage === "WON" || draft.stage === "LOST" ? { closingReason: draft.closingReason.trim() } : {}),
      ...(draft.stage === "WON" ? { actualValue: Number(draft.actualValue), actualCloseDate: parseDdMmYyyy(draft.actualCloseDate)! } : {}), lines,
    };
    setOpportunities(current => editing ? current.map(item => item.id === editing.id ? record : item) : [record, ...current]);
    setDialogOpen(false);
    setNotice(editing ? "Đã cập nhật cơ hội trong bản xem trước. Thay đổi chưa được lưu lên hệ thống." : "Đã tạo cơ hội trong bản xem trước. Thay đổi chưa được lưu lên hệ thống.");
  };

  return <div className="opportunity-page">
    <header className="opportunity-heading">
      <div><p className="eyebrow">Quản lý bán hàng</p><h1>Pipeline cơ hội bán hàng</h1><p>Theo dõi cơ hội theo giai đoạn và tổng giá trị dự kiến từ các dòng sản phẩm, dịch vụ.</p></div>
      {canWrite && <Button onClick={openCreate}>+ Thêm cơ hội</Button>}
    </header>
    {preview && <p className="opportunity-preview-note" role="note"><strong>Chế độ xem trước.</strong> Đang dùng dữ liệu mẫu; thao tác trên trang này không lưu vào hệ thống.</p>}
    {notice && <p className="opportunity-notice" role="status">{notice}</p>}

    <section className="offering-section opportunity-filters" aria-label="Tìm kiếm cơ hội">
      <div className="field"><label htmlFor="opportunity-search">Tìm cơ hội</label><input id="opportunity-search" className="input" value={search} onChange={event => setSearch(event.target.value)} placeholder="Tên cơ hội, khách hàng hoặc sản phẩm/dịch vụ" /></div>
      <p className="opportunity-result-count" aria-live="polite">{filtered.length} cơ hội</p>
    </section>

    {filtered.length === 0 ? <section className="offering-section"><EmptyState title={search ? "Không tìm thấy cơ hội" : "Chưa có cơ hội bán hàng"}>{search ? "Thử từ khóa khác để tìm cơ hội." : "Tạo cơ hội đầu tiên để bắt đầu theo dõi pipeline."}</EmptyState></section> : <div className="opportunity-board" role="region" aria-label="Pipeline theo giai đoạn">
      {opportunityStages.map(stage => {
        const items = filtered.filter(opportunity => opportunity.stage === stage);
        return <section className={`opportunity-column opportunity-column-${stage.toLowerCase()}`} key={stage} aria-labelledby={`opportunity-stage-${stage}`}>
          <header><h2 id={`opportunity-stage-${stage}`}>{stageLabels[stage]}</h2><span className="opportunity-count" aria-label={`${items.length} cơ hội`}>{items.length}</span></header>
          {items.length ? <ul className="opportunity-card-list">{items.map(opportunity => <li key={opportunity.id}><article className="opportunity-card" aria-labelledby={`opportunity-title-${opportunity.id}`}>
            <div className="opportunity-card-heading"><Badge tone={statusTone(stage)}>{stageLabels[stage]}</Badge>{editing?.id === opportunity.id && <span className="sr-only">Đang chỉnh sửa</span>}</div>
            <h3 id={`opportunity-title-${opportunity.id}`}>{opportunity.title}</h3>
            <p className="opportunity-customer">{opportunity.customerName}</p>
            <ul className="opportunity-lines" aria-label="Dòng sản phẩm hoặc dịch vụ">{opportunity.lines.map(line => <li key={line.id}><span>{line.offeringName} × {line.quantity}</span><small>Chiết khấu {line.discountPercent}%</small></li>)}</ul>
            <dl className="opportunity-card-summary"><div><dt>Giá trị dự kiến</dt><dd>{formatVnd(opportunity.expectedValue)}</dd></div><div><dt>Ngày dự kiến chốt</dt><dd>{dateLabel(opportunity.expectedCloseDate)}</dd></div></dl>
            {stage === "WON" && opportunity.actualValue !== undefined && <p className="opportunity-actual-value">Giá trị chốt <strong>{formatVnd(opportunity.actualValue)}</strong><span className={opportunity.actualValue >= opportunity.expectedValue ? "opportunity-variance-positive" : "opportunity-variance-negative"}>So với dự kiến: {formatVnd(opportunity.actualValue - opportunity.expectedValue)}</span>{opportunity.actualCloseDate && <small>Ngày chốt thực tế: {dateLabel(opportunity.actualCloseDate)}</small>}</p>}
            {opportunity.closingReason && <p className="opportunity-closing-reason"><strong>Lý do:</strong> {opportunity.closingReason}</p>}
            {canWrite && stage !== "WON" && stage !== "LOST" && <Button variant="secondary" className="opportunity-edit" onClick={() => openEdit(opportunity)}>Xem / chỉnh sửa</Button>}
          </article></li>)}</ul> : <p className="opportunity-column-empty">Chưa có cơ hội</p>}
        </section>;
      })}
    </div>}

    {dialogOpen && <OpportunityFormDialog onClose={() => setDialogOpen(false)} onSave={save} record={editing} customers={customers} offerings={offerings} />}
  </div>;
}

function OpportunityFormDialog({ onClose, onSave, record, customers, offerings }: { onClose: () => void; onSave: (draft: Draft) => void; record: OpportunityRecord | null; customers: OpportunityCustomer[]; offerings: OpportunityOffering[] }) {
  const modal = useModal(true, onClose);
  const [draft, setDraft] = useState<Draft>(() => record ? fromRecord(record) : blankDraft(offerings[0]));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [summaryError, setSummaryError] = useState("");
  const readOnly = Boolean(record && (record.stage === "WON" || record.stage === "LOST"));

  const update = (key: DraftScalarField, value: string | OpportunityStage) => { setDraft(current => ({ ...current, [key]: value })); setErrors(current => ({ ...current, [key]: "" })); setSummaryError(""); };
  const updateLine = (key: string, field: keyof Omit<DraftLine, "key">, value: string) => { setDraft(current => ({ ...current, lines: current.lines.map(line => {
    if (line.key !== key) return line;
    if (field === "offeringId") { const selected = offerings.find(item => item.id === value); return { ...line, offeringId: value, unitPrice: selected?.referencePrice.toString() ?? "" }; }
    return { ...line, [field]: value };
  }) })); setErrors({}); setSummaryError(""); };
  const addLine = () => { setDraft(current => ({ ...current, lines: [...current.lines, lineDraft(offerings[0])] })); setErrors({}); setSummaryError(""); };
  const removeLine = (key: string) => { setDraft(current => ({ ...current, lines: current.lines.length > 1 ? current.lines.filter(line => line.key !== key) : current.lines })); setErrors({}); setSummaryError(""); };
  const attrs = (name: string) => ({ "aria-invalid": Boolean(errors[name]), "aria-describedby": errors[name] ? `opportunity-error-${name.replaceAll(/[^a-z0-9-]/giu, "-")}` : undefined });
  const error = (name: string) => errors[name] ? <p className="opportunity-field-error" id={`opportunity-error-${name.replaceAll(/[^a-z0-9-]/giu, "-")}`}>{errors[name]}</p> : null;
  const stageOptions = record ? allowedNextStages(record.stage) : ["NEW" as const];
  const lineValues = draft.lines.map(line => ({ quantity: Number(line.quantity), unitPrice: Number(line.unitPrice), discountPercent: Number(line.discountPercent) }));
  const previewValue = draft.lines.length > 0 && draft.lines.every(lineIsValid) ? expectedValue(lineValues) : null;

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next: Record<string, string> = {};
    const expectedCloseDate = parseDdMmYyyy(draft.expectedCloseDate);
    const actualCloseDate = parseDdMmYyyy(draft.actualCloseDate);
    if (!draft.title.trim()) next.title = "Vui lòng nhập tên cơ hội.";
    if (!draft.customerId) next.customerId = "Vui lòng chọn khách hàng.";
    if (!draft.expectedCloseDate.trim()) next.expectedCloseDate = "Vui lòng nhập ngày dự kiến chốt.";
    else if (!expectedCloseDate) next.expectedCloseDate = "Ngày dự kiến chốt phải là ngày hợp lệ theo định dạng dd/MM/yyyy.";
    else if (expectedCloseDate < todayIso()) next.expectedCloseDate = "Ngày dự kiến chốt phải từ hôm nay trở đi.";
    if (!draft.lines.length) next.lines = "Cơ hội cần ít nhất một dòng sản phẩm/dịch vụ.";
    draft.lines.forEach((line, index) => {
      const prefix = `line-${index + 1}`;
      const quantity = Number(line.quantity), unitPrice = Number(line.unitPrice), discount = Number(line.discountPercent);
      if (!line.offeringId) next[`${prefix}-offering`] = "Vui lòng chọn sản phẩm/dịch vụ.";
      if (!Number.isInteger(quantity) || quantity <= 0) next[`${prefix}-quantity`] = "Số lượng phải là số nguyên lớn hơn 0.";
      if (!line.unitPrice.trim() || !Number.isFinite(unitPrice) || unitPrice < 0) next[`${prefix}-price`] = "Đơn giá phải là số không âm.";
      if (!line.discountPercent.trim() || !Number.isFinite(discount) || discount < 0 || discount > 100) next[`${prefix}-discount`] = "Chiết khấu phải từ 0% đến 100%.";
    });
    if ((draft.stage === "WON" || draft.stage === "LOST") && !draft.closingReason.trim()) next.closingReason = "Vui lòng nhập lý do chốt cơ hội.";
    if (draft.stage === "WON" && (!draft.actualValue.trim() || !Number.isFinite(Number(draft.actualValue)) || Number(draft.actualValue) < 0)) next.actualValue = "Giá trị chốt phải là số không âm.";
    if (draft.stage === "WON" && !draft.actualCloseDate.trim()) next.actualCloseDate = "Vui lòng nhập ngày chốt thực tế.";
    else if (draft.stage === "WON" && !actualCloseDate) next.actualCloseDate = "Ngày chốt thực tế phải là ngày hợp lệ theo định dạng dd/MM/yyyy.";
    else if (draft.stage === "WON" && actualCloseDate && actualCloseDate > todayIso()) next.actualCloseDate = "Ngày chốt thực tế không thể ở tương lai.";
    if (Object.keys(next).length) { setErrors(next); setSummaryError("Vui lòng kiểm tra các trường được đánh dấu."); return; }
    onSave(draft);
  }

  return <dialog {...modal} className="dialog opportunity-dialog" aria-modal="true" aria-labelledby="opportunity-dialog-title">
    <h2 id="opportunity-dialog-title">{record ? "Chỉnh sửa cơ hội" : "Tạo cơ hội bán hàng"}</h2>
    <p className="helper">Một cơ hội có thể gồm nhiều dòng sản phẩm hoặc dịch vụ. Giá trị dự kiến được tổng hợp từ các dòng.</p>
    {summaryError && <Alert>{summaryError}</Alert>}
    {offerings.length === 0 && <Alert>Không có sản phẩm hoặc dịch vụ đang hoạt động trong phạm vi được cấp.</Alert>}
    <form className="opportunity-form" onSubmit={submit} noValidate>
      <div className="field"><label htmlFor="opportunity-title">Tên cơ hội *</label><input id="opportunity-title" className="input" value={draft.title} maxLength={160} required disabled={readOnly} {...attrs("title")} onChange={event => update("title", event.target.value)} />{error("title")}</div>
      <div className="field"><label htmlFor="opportunity-customer">Khách hàng *</label><select id="opportunity-customer" className="input" value={draft.customerId} required disabled={readOnly} {...attrs("customerId")} onChange={event => update("customerId", event.target.value)}><option value="">Chọn khách hàng</option>{customers.map(customer => <option value={customer.id} key={customer.id}>{customer.name}</option>)}</select>{error("customerId")}</div>
      <div className="opportunity-form-grid">
        <div className="field"><label htmlFor="opportunity-close-date">Ngày dự kiến chốt *</label><DateInput id="opportunity-close-date" min={todayIso()} value={draft.expectedCloseDate} required disabled={readOnly} {...attrs("expectedCloseDate")} onChange={value => update("expectedCloseDate", value)} />{error("expectedCloseDate")}</div>
        {record && <div className="field"><label htmlFor="opportunity-stage">Giai đoạn</label><select id="opportunity-stage" className="input" value={draft.stage} disabled={readOnly} onChange={event => update("stage", event.target.value as OpportunityStage)}>{stageOptions.map(stage => <option value={stage} key={stage}>{stageLabels[stage]}</option>)}</select></div>}
      </div>
      {(draft.stage === "WON" || draft.stage === "LOST") && <div className="field"><label htmlFor="opportunity-closing-reason">Lý do chốt *</label><textarea id="opportunity-closing-reason" className="input opportunity-textarea" rows={3} value={draft.closingReason} required disabled={readOnly} {...attrs("closingReason")} onChange={event => update("closingReason", event.target.value)} />{error("closingReason")}</div>}
      {draft.stage === "WON" && <div className="opportunity-form-grid"><div className="field"><label htmlFor="opportunity-actual-value">Giá trị chốt (VND) *</label><input id="opportunity-actual-value" className="input" type="number" min="0" step="0.01" value={draft.actualValue} disabled={readOnly} {...attrs("actualValue")} onChange={event => update("actualValue", event.target.value)} />{error("actualValue")}</div><div className="field"><label htmlFor="opportunity-actual-close-date">Ngày chốt thực tế *</label><DateInput id="opportunity-actual-close-date" max={todayIso()} value={draft.actualCloseDate} disabled={readOnly} {...attrs("actualCloseDate")} onChange={value => update("actualCloseDate", value)} />{error("actualCloseDate")}</div></div>}
      <section className="opportunity-line-editor" aria-labelledby="opportunity-lines-heading">
        <header><div><h3 id="opportunity-lines-heading">Sản phẩm/dịch vụ</h3><p className="helper">Thêm ít nhất một dòng; chiết khấu nhập theo phần trăm.</p></div><Button type="button" variant="secondary" disabled={readOnly || offerings.length === 0} onClick={addLine}>+ Thêm dòng</Button></header>
        {error("lines")}
        {draft.lines.map((line, index) => {
          const key = `line-${index + 1}`;
          const amount = lineIsValid(line) ? expectedValue([{ quantity: Number(line.quantity), unitPrice: Number(line.unitPrice), discountPercent: Number(line.discountPercent) }]) : null;
          return <fieldset className="opportunity-line" key={line.key} disabled={readOnly}>
            <legend>Dòng {index + 1}</legend>
            {draft.lines.length > 1 && !readOnly && <Button type="button" variant="danger" className="opportunity-line-remove" onClick={() => removeLine(line.key)}><TrashIcon aria-hidden="true" />Xóa dòng {index + 1}</Button>}
            <div className="field"><label htmlFor={`${key}-offering`}>Sản phẩm/dịch vụ *</label><select id={`${key}-offering`} className="input" value={line.offeringId} required {...attrs(`${key}-offering`)} onChange={event => updateLine(line.key, "offeringId", event.target.value)}><option value="">Chọn</option>{offerings.map(offering => <option key={offering.id} value={offering.id}>{offering.name}</option>)}</select>{error(`${key}-offering`)}</div>
            <div className="opportunity-line-grid">
              <div className="field"><label htmlFor={`${key}-quantity`}>Số lượng *</label><input id={`${key}-quantity`} className="input" type="number" min="1" step="1" value={line.quantity} {...attrs(`${key}-quantity`)} onChange={event => updateLine(line.key, "quantity", event.target.value)} />{error(`${key}-quantity`)}</div>
              <div className="field"><label htmlFor={`${key}-price`}>Đơn giá (VND) *</label><input id={`${key}-price`} className="input" type="number" min="0" step="0.01" value={line.unitPrice} {...attrs(`${key}-price`)} onChange={event => updateLine(line.key, "unitPrice", event.target.value)} />{error(`${key}-price`)}</div>
              <div className="field"><label htmlFor={`${key}-discount`}>Chiết khấu (%) *</label><input id={`${key}-discount`} className="input" type="number" min="0" max="100" step="0.01" value={line.discountPercent} {...attrs(`${key}-discount`)} onChange={event => updateLine(line.key, "discountPercent", event.target.value)} />{error(`${key}-discount`)}</div>
            </div>
            <p className="opportunity-line-total">Giá trị dòng dự kiến: <strong>{amount === null ? "—" : formatVnd(amount)}</strong></p>
          </fieldset>;
        })}
      </section>
      <div className="field"><label htmlFor="opportunity-notes">Ghi chú</label><textarea id="opportunity-notes" className="input opportunity-textarea" rows={3} maxLength={4000} value={draft.notes} disabled={readOnly} onChange={event => update("notes", event.target.value)} /></div>
      <p className="opportunity-total">Tổng giá trị dự kiến <strong>{previewValue === null ? "—" : formatVnd(previewValue)}</strong></p>
      <footer className="dialog-actions"><Button type="button" variant="secondary" onClick={onClose}>Đóng</Button>{!readOnly && <Button type="submit" disabled={offerings.length === 0}>{record ? "Lưu thay đổi" : "Tạo cơ hội"}</Button>}</footer>
    </form>
  </dialog>;
}
