@echo off
setlocal
cd /d "%~dp0"

echo ============================================
echo   Hodoor - رفع المشروع على GitHub وبناء APK
echo ============================================
echo.

git rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 (
    echo [*] تهيئة git...
    git init
    git branch -M main
)

echo [*] إضافة الملفات...
git add .
git commit -m "Hodoor attendance app with biometric check-in/out" 2>nul
if errorlevel 1 (
    echo [i] لا يوجد تغييرات جديدة للـ commit ^(عادي^).
)

where gh >nul 2>&1
if %errorlevel%==0 (
    echo [*] تم العثور على GitHub CLI. جاري إنشاء الريبو ورفع الكود...
    git remote get-url origin >nul 2>&1
    if errorlevel 1 (
        gh repo create hodoor --private --source=. --push
    ) else (
        git push -u origin main
    )
    echo.
    echo [OK] تم الرفع. جاري فتح تبويب Actions في المتصفح...
    gh browse --repo hodoor 2>nul
    echo.
    echo افتح تبويب Actions -^> Build APK -^> بعد ما يخلص نزّل hodoor-debug-apk من Artifacts.
) else (
    echo [!] GitHub CLI ^(gh^) غير مثبّت.
    echo     الطريقة اليدوية:
    echo       1^) اعمل ريبو فاضي باسم hodoor من موقع github.com
    echo       2^) شغّل الأوامر دي:
    echo.
    echo          git remote add origin https://github.com/YOUR_USERNAME/hodoor.git
    echo          git push -u origin main
    echo.
    echo     أو ثبّت GitHub CLI من https://cli.github.com وأعد تشغيل السكربت.
)

echo.
pause
