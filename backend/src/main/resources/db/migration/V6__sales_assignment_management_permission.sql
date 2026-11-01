INSERT INTO public.permissions (id, code, description, created_at)
SELECT '7c4500c4-9b38-4a5c-92de-27d8d9b00124', 'sales_assignment.manage',
       'Manage Sales Assignments within the current organization and team scope.', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM public.permissions WHERE code='sales_assignment.manage'
);

INSERT INTO public.role_permissions (role_id, permission_id, granted_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM public.roles r
CROSS JOIN public.permissions p
WHERE r.code IN ('ADMIN','MANAGER')
  AND r.status='ACTIVE'
  AND p.code='sales_assignment.manage'
  AND NOT EXISTS (
      SELECT 1 FROM public.role_permissions existing
      WHERE existing.role_id=r.id AND existing.permission_id=p.id
  );
