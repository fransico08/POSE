"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/components/session/SessionGate";
import { Alert } from "@/components/ui/Alert";
import { Button } from "@/components/ui/Button";
import { api } from "@/lib/api";
import { canManageOfferings, fieldLabel, formatOfferingDate, offeringErrorMessage, offeringFieldErrors, type Offering, type OfferingCategory, type OfferingTeamPage } from "@/lib/offerings";

interface OfferingFormValues {
  name: string;
  kind: "PRODUCT" | "SERVICE";
  categoryId: string;
  teamId: string;
  referencePrice: string;
  unit: string;
  description: string;
  active: boolean;
}

const EMPTY_FORM: OfferingFormValues = { name: "", kind: "PRODUCT", categoryId: "", teamId: "", referencePrice: "", unit: "", description: "", active: true };
const FIELD_IDS: Record<string, string> = { name: "offering-name", kind: "offering-kind", categoryId: "offering-category", teamId: "offering-team", referencePrice: "offering-price", unit: "offering-unit", description: "offering-description", active: "offering-active" };

export function OfferingForm({ id }: { id: string | null }) {
  const { user } = useSession();
  const router = useRouter();
  const queryClient = useQueryClient();
  const manager = canManageOfferings(user.roles, user.permissions);
  const [form, setForm] = useState(EMPTY_FORM);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");
  const errorSummaryRef = useRef<HTMLDivElement>(null);
  const initializedRef = useRef(false);
  const categories = useQuery({
    queryKey: ["offering-categories", user.organization.id, user.id],
    queryFn: () => api.request<OfferingCategory[]>("/api/backend/offerings/categories"),
    enabled: manager,
  });
  const teams = useQuery({
    queryKey: ["offering-teams", user.organization.id, user.id],
    queryFn: () => api.request<OfferingTeamPage>("/api/backend/teams?page=0&size=100"),
    enabled: manager,
  });
  const detail = useQuery({
    queryKey: ["offering", user.organization.id, user.id, id],
    queryFn: () => api.request<Offering>(`/api/backend/offerings/${encodeURIComponent(id!)}`),
    enabled: manager && Boolean(id),
  });

  useEffect(() => {
    if (!id || !detail.data || initializedRef.current) return;
    setForm({
      name: detail.data.name,
      kind: detail.data.kind,
      categoryId: detail.data.categoryId,
      teamId: detail.data.teamId ?? "",
      referencePrice: detail.data.referencePrice ?? "",
      unit: detail.data.unit ?? "",
      description: detail.data.description ?? "",
      active: detail.data.active,
    });
    initializedRef.current = true;
  }, [detail.data, id]);

  useEffect(() => {
    if (Object.keys(fieldErrors).length > 0) errorSummaryRef.current?.focus();
  }, [fieldErrors]);

  const mutation = useMutation({
    mutationFn: async (values: OfferingFormValues) => {
      const payload = {
        name: values.name.trim(), kind: values.kind, categoryId: values.categoryId,
        teamId: values.teamId, description: values.description.trim() || null,
        unit: values.unit.trim() || null, referencePrice: values.referencePrice,
        ...(id ? { expectedUpdatedAt: detail.data?.updatedAt } : { active: values.active }),
      };
      return id
        ? api.request<Offering>(`/api/backend/offerings/${encodeURIComponent(id)}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) })
        : api.request<Offering>("/api/backend/offerings", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
    },
    onSuccess: async saved => {
      queryClient.setQueryData(["offering", user.organization.id, user.id, saved.id], saved);
      await queryClient.invalidateQueries({ queryKey: ["offerings", user.organization.id, user.id] });
      window.sessionStorage.setItem("pose-offering-saved", saved.id);
      router.push(`/app/san-pham/${saved.id}`);
    },
  });

  function update<K extends keyof OfferingFormValues>(field: K, value: OfferingFormValues[K]) {
    setForm(current => ({ ...current, [field]: value }));
    setFieldErrors(current => { const next = { ...current }; delete next[field]; return next; });
    setFormError("");
  }

  function validate() {
    const errors: Record<string, string> = {};
    if (!form.name.trim()) errors.name = "Nhập tên sản phẩm hoặc dịch vụ.";
    else if (form.name.trim().length > 200) errors.name = "Tên tối đa 200 ký tự.";
    if (!form.categoryId) errors.categoryId = "Chọn danh mục đang hoạt động.";
    if (!form.teamId) errors.teamId = "Chọn team sở hữu sản phẩm hoặc dịch vụ.";
    if (!form.referencePrice) errors.referencePrice = "Nhập giá tham khảo.";
    else if (!/^\d{1,16}(?:\.\d{1,2})?$/.test(form.referencePrice) || !Number.isFinite(Number(form.referencePrice))) errors.referencePrice = "Giá phải từ 0 đến 9999999999999999.99, tối đa hai chữ số thập phân.";
    if (form.unit.length > 80) errors.unit = "Đơn vị tối đa 80 ký tự.";
    if (form.description.length > 4000) errors.description = "Mô tả tối đa 4000 ký tự.";
    return errors;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError("");
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    try {
      await mutation.mutateAsync(form);
    } catch (error) {
      const serverErrors = offeringFieldErrors(error);
      if (Object.keys(serverErrors).length > 0) setFieldErrors(serverErrors);
      else setFormError(offeringErrorMessage(error));
    }
  }

  function fieldError(field: keyof OfferingFormValues) {
    const value = fieldErrors[field];
    return value ? <p className="offering-field-error" id={`${FIELD_IDS[field]}-error`}>{value}</p> : null;
  }

  if (!manager) return <section className="offering-section"><Alert>Chỉ Manager hoặc Admin có quyền quản lý sản phẩm và dịch vụ.</Alert><Link className="button button-secondary offering-back" href="/app/san-pham">Quay lại danh sách</Link></section>;
  if (id && detail.isPending) return <section className="offering-section" aria-busy="true"><p role="status">Đang tải thông tin sản phẩm hoặc dịch vụ…</p><div className="offering-skeleton" /><div className="offering-skeleton" /></section>;
  if (id && detail.isError) return <section className="offering-section"><Alert>{offeringErrorMessage(detail.error)}</Alert><Button variant="secondary" onClick={() => void detail.refetch()}>Thử tải lại</Button><Link className="button button-ghost offering-back" href="/app/san-pham">Về danh sách</Link></section>;

  const categoriesReady = categories.data ?? [];
  const teamsReady = teams.data?.items ?? [];
  const existingCategoryMissing = Boolean(id && form.categoryId && !categoriesReady.some(category => category.id === form.categoryId));
  const existingTeamMissing = Boolean(id && form.teamId && !teamsReady.some(team => team.id === form.teamId));
  const currency = detail.data?.currencyCode?.trim() || "VND";
  const hasLoadError = categories.isError || teams.isError;

  return <div className="offering-page offering-narrow">
    <header className="offering-heading"><div><p className="eyebrow"><Link href="/app/san-pham">Sản phẩm &amp; dịch vụ</Link> / {id ? "Chỉnh sửa" : "Thêm mới"}</p><h1>{id ? "Chỉnh sửa sản phẩm hoặc dịch vụ" : "Thêm sản phẩm hoặc dịch vụ"}</h1><p>Điền thông tin danh mục. Mã được hệ thống tạo tự động.</p></div></header>
    {hasLoadError && <section className="offering-inline-error"><Alert>Không tải được danh mục hoặc team. Hãy thử tải lại trước khi lưu.</Alert><Button variant="secondary" onClick={() => { void categories.refetch(); void teams.refetch(); }}>Thử tải lại</Button></section>}
    {(categories.isPending || teams.isPending) && <p className="offering-load-hint" role="status">Đang tải danh mục và team…</p>}
    {fieldErrors.expectedUpdatedAt && <div className="offering-inline-error"><Alert>{fieldErrors.expectedUpdatedAt}</Alert><Button variant="secondary" onClick={() => { initializedRef.current = false; void detail.refetch(); setFieldErrors({}); }}>Tải bản mới nhất</Button></div>}
    {formError && <Alert>{formError}</Alert>}
    {Object.keys(fieldErrors).length > 0 && <div className="offering-error-summary" ref={errorSummaryRef} tabIndex={-1} role="alert" aria-labelledby="offering-errors-title"><h2 id="offering-errors-title">Kiểm tra lại thông tin</h2><ul>{Object.entries(fieldErrors).map(([field, message]) => <li key={field}><a href={`#${FIELD_IDS[field] ?? FIELD_IDS.name}`}>{fieldLabel(field)}: {message}</a></li>)}</ul></div>}
    <form className="offering-section offering-form" onSubmit={event => void submit(event)} noValidate>
      <section aria-labelledby="offering-form-section"><header className="offering-section-heading"><h2 id="offering-form-section">Thông tin sản phẩm hoặc dịch vụ</h2><p>Các trường có dấu * là bắt buộc.</p></header>
        <div className="offering-form-grid">
          <div className="field offering-field-full"><label htmlFor={FIELD_IDS.name}>Tên <span aria-hidden="true">*</span></label><input className="input" id={FIELD_IDS.name} value={form.name} maxLength={200} autoComplete="off" required aria-invalid={Boolean(fieldErrors.name)} aria-describedby={fieldErrors.name ? `${FIELD_IDS.name}-error` : undefined} onChange={event => update("name", event.target.value)} />{fieldError("name")}</div>
          <div className="field"><label htmlFor={FIELD_IDS.kind}>Loại <span aria-hidden="true">*</span></label><select className="input" id={FIELD_IDS.kind} value={form.kind} required aria-invalid={Boolean(fieldErrors.kind)} aria-describedby={fieldErrors.kind ? `${FIELD_IDS.kind}-error` : undefined} onChange={event => update("kind", event.target.value as OfferingFormValues["kind"])}><option value="PRODUCT">Sản phẩm</option><option value="SERVICE">Dịch vụ</option></select>{fieldError("kind")}</div>
          <div className="field"><label htmlFor={FIELD_IDS.categoryId}>Danh mục <span aria-hidden="true">*</span></label><select className="input" id={FIELD_IDS.categoryId} value={form.categoryId} required aria-invalid={Boolean(fieldErrors.categoryId)} aria-describedby={fieldErrors.categoryId ? `${FIELD_IDS.categoryId}-error` : undefined} onChange={event => update("categoryId", event.target.value)}><option value="">Chọn danh mục đang hoạt động</option>{existingCategoryMissing && <option value={form.categoryId} disabled>{detail.data?.categoryName ?? "Danh mục cũ không còn hoạt động"}</option>}{categoriesReady.map(category => <option value={category.id} key={category.id}>{category.name}</option>)}</select>{fieldError("categoryId")}</div>
          <div className="field"><label htmlFor={FIELD_IDS.teamId}>Team sở hữu <span aria-hidden="true">*</span></label><select className="input" id={FIELD_IDS.teamId} value={form.teamId} required aria-invalid={Boolean(fieldErrors.teamId)} aria-describedby={fieldErrors.teamId ? `${FIELD_IDS.teamId}-error` : undefined} onChange={event => update("teamId", event.target.value)}><option value="">Chọn team</option>{existingTeamMissing && <option value={form.teamId} disabled>{detail.data?.teamName ?? "Team cũ không còn trong phạm vi"}</option>}{teamsReady.map(team => <option value={team.id} key={team.id} disabled={team.status !== "ACTIVE"}>{team.name}{team.status === "INACTIVE" ? " (Ngừng hoạt động)" : ""}</option>)}</select>{fieldError("teamId")}</div>
          <div className="field"><label htmlFor={FIELD_IDS.price}>Giá tham khảo ({currency}) <span aria-hidden="true">*</span></label><input className="input" id={FIELD_IDS.price} type="text" inputMode="decimal" value={form.referencePrice} maxLength={19} placeholder="0.00" required aria-invalid={Boolean(fieldErrors.referencePrice)} aria-describedby={fieldErrors.referencePrice ? `${FIELD_IDS.price}-error` : `${FIELD_IDS.price}-helper`} onChange={event => update("referencePrice", event.target.value)} /><p className="helper" id={`${FIELD_IDS.price}-helper`}>Không âm, tối đa hai chữ số thập phân. Loại tiền hiện có được giữ nguyên khi cập nhật.</p>{fieldError("referencePrice")}</div>
          <div className="field"><label htmlFor={FIELD_IDS.unit}>Đơn vị</label><input className="input" id={FIELD_IDS.unit} value={form.unit} maxLength={80} placeholder="Ví dụ: tháng, bộ" aria-invalid={Boolean(fieldErrors.unit)} aria-describedby={fieldErrors.unit ? `${FIELD_IDS.unit}-error` : undefined} onChange={event => update("unit", event.target.value)} />{fieldError("unit")}</div>
          <div className="field offering-field-full"><label htmlFor={FIELD_IDS.description}>Mô tả</label><textarea className="input offering-textarea" id={FIELD_IDS.description} value={form.description} maxLength={4000} rows={5} aria-invalid={Boolean(fieldErrors.description)} aria-describedby={fieldErrors.description ? `${FIELD_IDS.description}-error` : undefined} onChange={event => update("description", event.target.value)} />{fieldError("description")}<p className="helper">{form.description.length}/4000 ký tự</p></div>
          {!id && <div className="offering-active-control offering-field-full"><input id={FIELD_IDS.active} type="checkbox" checked={form.active} aria-invalid={Boolean(fieldErrors.active)} aria-describedby={fieldErrors.active ? `${FIELD_IDS.active}-error` : undefined} onChange={event => update("active", event.target.checked)} /><label htmlFor={FIELD_IDS.active}>Kích hoạt ngay sau khi tạo</label>{fieldError("active")}</div>}
          {id && <p className="offering-status-note offering-field-full">Trạng thái hiện tại: <strong>{detail.data?.active ? "Đang hoạt động" : "Ngừng hoạt động"}</strong>. Thay đổi trạng thái tại trang chi tiết để xác nhận riêng.</p>}
        </div>
      </section>
      <footer className="offering-form-actions"><Link className="button button-secondary" href={id ? `/app/san-pham/${id}` : "/app/san-pham"}>Hủy</Link><Button type="submit" busy={mutation.isPending} disabled={hasLoadError || categories.isPending || teams.isPending}>{mutation.isPending ? "Đang lưu…" : id ? "Lưu thay đổi" : "Tạo sản phẩm/dịch vụ"}</Button></footer>
    </form>
    {id && detail.data && <p className="offering-last-updated">Cập nhật lần cuối: {formatOfferingDate(detail.data.updatedAt)}</p>}
  </div>;
}
