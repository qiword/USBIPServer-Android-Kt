@echo off
setlocal

:: =============================================================
:: Build script for USB/IP Server for Android (Kotlin)
:: =============================================================

:: Configure paths
set "JAVA_HOME=C:\App\DevTools\Android\Android Studio\jbr"
set "ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk"
set "ADB=C:\App\platform-tools\adb.exe"

:: Switch to project directory
cd /d "%~dp0"

echo.
echo ======================================
echo   USB/IP Server for Android (Kotlin) - Build
echo ======================================
echo.

:: =============================================================
:: Step 1: Build the APK (pure Kotlin + JNA, no native compile)
:: =============================================================
echo [1/2] Building APK...

call gradlew.bat assembleDebug
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] APK build failed!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [1/2] APK build OK.

:: =============================================================
:: Step 2: Install to device
:: =============================================================
echo.
echo [2/2] Installing to device...

:: APK path
set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"

if not exist "%APK_PATH%" (
    echo [ERROR] APK not found: %APK_PATH%
    pause
    exit /b 1
)

:: List connected devices
echo ======================================
echo   Connected devices:
echo ======================================
%ADB% devices
echo.

:: Find the first connected device
for /f "skip=1 tokens=1" %%i in ('%ADB% devices ^| findstr /r /c:"[0-9a-fA-F].*device"') do (
    set "DEVICE_SERIAL=%%i"
    goto :install
)

echo [ERROR] No device connected.
pause
exit /b 1

:install
echo Installing APK on device [%DEVICE_SERIAL%]...
%ADB% -s %DEVICE_SERIAL% install -r "%APK_PATH%"

if %ERRORLEVEL% equ 0 (
    echo.
    echo [OK] Install succeeded!
) else (
    echo.
    echo [ERROR] Install failed!
)

echo.
echo ======================================
echo   Done.
echo ======================================
pause
