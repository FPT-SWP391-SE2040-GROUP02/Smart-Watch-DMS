@echo off
chcp 65001 >nul
title Cai dat Watch App len Galaxy Watch
cd /d "%~dp0"

echo =====================================================================
echo       LEGACYVAULT WATCH - KET NOI VA CAI DAT LEN GALAXY WATCH
echo =====================================================================
echo.

set ADB_PATH=C:\Users\ThanhDuy\Downloads\platform-tools-latest-windows\platform-tools\adb.exe
if not exist "%ADB_PATH%" (
    where adb >nul 2>&1
    if %errorlevel%==0 (
        set ADB_PATH=adb
    ) else (
        echo [LOI] Khong tim thay adb.exe tai platform-tools!
        echo Vui long kiem tra thu muc Downloads hoac them adb vao bien moi truong PATH.
        pause
        exit /b 1
    )
)

set WATCH_IP=192.168.1.236:5555
set /p custom_ip="Nhap IP Galaxy Watch [Mac dinh: %WATCH_IP%]: "
if not "%custom_ip%"=="" set WATCH_IP=%custom_ip%

echo.
echo [1/3] Dang ket noi toi Galaxy Watch tai %WATCH_IP%...
"%ADB_PATH%" connect %WATCH_IP%
"%ADB_PATH%" devices

echo.
echo [2/3] Kiem tra file APK...
set APK_PATH=%~dp0apk\LegacyVault-Watch-debug.apk
if not exist "%APK_PATH%" (
    set APK_PATH=%~dp0watch-app\app\build\outputs\apk\debug\app-debug.apk
)
if not exist "%APK_PATH%" (
    echo Khong tim thay APK san co, dang tien hanh build APK bang Gradle...
    cd /d "%~dp0watch-app"
    call gradlew.bat assembleDebug
    cd /d "%~dp0"
)

if not exist "%APK_PATH%" (
    echo [LOI] Build APK that bai hoac khong tim thay file APK!
    pause
    exit /b 1
)

echo.
echo [3/3] Dang cai dat app-debug.apk len Galaxy Watch...
"%ADB_PATH%" install -r "%APK_PATH%"

echo.
echo =====================================================================
echo Cai dat hoan tat! Ung dung 'LegacyVault Watch' da san sang tren dong ho.
echo =====================================================================
pause
