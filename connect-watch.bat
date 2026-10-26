@echo off
chcp 65001 >nul
title Ket Noi Galaxy Watch
set ADB_PATH=C:\Users\ThanhDuy\Downloads\platform-tools-latest-windows\platform-tools\adb.exe
if not exist "%ADB_PATH%" (
    where adb >nul 2>&1
    if %errorlevel%==0 (set ADB_PATH=adb) else (
        echo [LOI] Khong tim thay adb.exe!
        pause
        exit /b 1
    )
)

set WATCH_IP=192.168.1.236:5555
set /p custom_ip="Nhap IP Galaxy Watch [Mac dinh: %WATCH_IP%]: "
if not "%custom_ip%"=="" set WATCH_IP=%custom_ip%
rem Neu chi nhap IP khong co port, adb dung port mac dinh 5555
echo %WATCH_IP% | findstr /C:":" >nul
if errorlevel 1 set WATCH_IP=%WATCH_IP%:5555

echo.
echo Dang ket noi toi Galaxy Watch tai %WATCH_IP%...
"%ADB_PATH%" connect %WATCH_IP% > "%TEMP%\adb_connect.log" 2>&1
type "%TEMP%\adb_connect.log" | findstr /C:"connected" >nul
if errorlevel 1 (
    echo [LOI] Khong ket noi duoc! Kiem tra dong ho trong muc SmartThings > More options > Developer options.
) else (
    echo Ket noi thanh cong.
)
echo.
echo Thiet bi adb hien tai:
"%ADB_PATH%" devices
pause
