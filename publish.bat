@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

echo ============================================
echo   Hodoor - رفع التحديثات وبناء APK
echo ============================================
echo.

git add .
git commit -m "Fix Android build dependencies for CI" 2>nul
if errorlevel 1 (
    echo [i] لا يوجد تغييرات جديدة للـ commit.
)

git push origin main 2>nul
if errorlevel 1 (
    git push origin master
)

echo.
echo [OK] تم الرفع.
echo افتح: https://github.com/abdelrahman437/hodoor/actions
echo بعد ما يخلص Build APK نزّل hodoor-debug-apk من Artifacts.
echo.
pause
