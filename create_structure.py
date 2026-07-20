import os

dirs = [
    # Backend - Spring Boot
    "backend/src/main/java/com/crmix/auth/controller",
    "backend/src/main/java/com/crmix/auth/service",
    "backend/src/main/java/com/crmix/auth/dto",
    "backend/src/main/java/com/crmix/auth/repository",
    "backend/src/main/java/com/crmix/user",
    "backend/src/main/java/com/crmix/customer",
    "backend/src/main/java/com/crmix/contract",
    "backend/src/main/java/com/crmix/activity",
    "backend/src/main/java/com/crmix/pipeline",
    "backend/src/main/java/com/crmix/report",
    "backend/src/main/java/com/crmix/notification",
    "backend/src/main/java/com/crmix/scheduler",
    "backend/src/main/java/com/crmix/common/config",
    "backend/src/main/java/com/crmix/common/exception",
    "backend/src/main/java/com/crmix/common/util",
    "backend/src/main/resources/db/migration",
    "backend/src/test/java/com/crmix",
    
    # Frontend - React + Vite
    "frontend/public",
    "frontend/src/assets/images",
    "frontend/src/assets/styles",
    "frontend/src/components/common",
    "frontend/src/components/layout",
    "frontend/src/features/auth",
    "frontend/src/features/customer",
    "frontend/src/features/contract",
    "frontend/src/features/pipeline",
    "frontend/src/features/dashboard",
    "frontend/src/hooks",
    "frontend/src/layouts",
    "frontend/src/pages",
    "frontend/src/routes",
    "frontend/src/services/api",
    "frontend/src/store",
    "frontend/src/utils",
    "frontend/tests",
    
    # Database
    "database/scripts",
    "database/diagrams",
    "database/data/seed",
    
    # Deploy
    "deploy/docker",
    "deploy/nginx",
    "deploy/scripts",
    
    # Docs
    "docs/api",
    "docs/design/ui-ux",
    "docs/requirements",
    "docs/meetings",
    "docs/testing"
]

base_dir = r"d:\POSE"
for d in dirs:
    path = os.path.normpath(os.path.join(base_dir, d))
    os.makedirs(path, exist_ok=True)
    # create a simple .gitkeep to ensure empty folders are tracked in git if needed
    with open(os.path.join(path, ".gitkeep"), "w") as f:
        pass

print("Cấu trúc thư mục đã được tạo thành công.")
