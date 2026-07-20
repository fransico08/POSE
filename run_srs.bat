@echo off
chcp 65001 > nul
echo ============================================
echo  Tao file SRS CRM - POSE Project
echo ============================================
echo.
echo [Buoc 1] Kiem tra va cai python-docx...
pip install python-docx --quiet
echo.
echo [Buoc 2] Chay script tao file Word...
python "d:\POSE\generate_srs.py"
echo.
if %ERRORLEVEL% EQU 0 (
    echo ============================================
    echo  THANH CONG! File SRS da duoc tao:
    echo  d:\POSE\SRS_CRM_SME_CRMix.docx
    echo ============================================
    echo.
    echo Mo file vua tao...
    start "" "d:\POSE\SRS_CRM_SME_CRMix.docx"
) else (
    echo ============================================
    echo  LOI! Vui long kiem tra lai.
    echo  Thu chay truc tiep:
    echo    pip install python-docx
    echo    python d:\POSE\generate_srs.py
    echo ============================================
)
pause
