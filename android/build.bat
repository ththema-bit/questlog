@echo off
rem Baut die Test-Version der Android-App.
rem Voraussetzung: JDK 17+ und Android-SDK (kommt mit Android Studio), siehe README.md.
setlocal
cd /d "%~dp0"

call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo Build fehlgeschlagen. Fehlt das Android-SDK? Siehe README.md.
    pause
    exit /b 1
)

echo.
echo Fertig: %~dp0app\build\outputs\apk\debug\app-debug.apk
pause
