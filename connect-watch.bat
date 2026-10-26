@echo off
title Ket Noi Galaxy Watch

set "ADB_PATH=%~dp0platform-tools-latest-windows\platform-tools\adb.exe"
if not exist "%ADB_PATH%" (
    set "ADB_PATH=C:\Users\ThanhDuy\Downloads\platform-tools-latest-windows\platform-tools\adb.exe"
    if not exist "%ADB_PATH%" (
        where adb >nul 2>&1
        if errorlevel 1 (
            echo [LOI] Khong tim thay adb.exe!
            pause
            exit /b 1
        )
        set "ADB_PATH=adb"
    )
)

set "DEFAULT_IP=10.146.42.15:33515"
set "WATCH_IP="
set /p WATCH_IP="Nhap IP Galaxy Watch [Mac dinh: %DEFAULT_IP%]: "
if not defined WATCH_IP set "WATCH_IP=%DEFAULT_IP%"
rem Neu chi nhap IP khong co port, adb dung port mac dinh 5555
echo %WATCH_IP% | findstr /C:":" >nul
if errorlevel 1 set "WATCH_IP=%WATCH_IP%:5555"

echo.
echo Dang ket noi toi Galaxy Watch tai %WATCH_IP% (toi da 5 lan, cach 3s)...
set "ATTEMPT=1"
:retry_loop
if %ATTEMPT% GTR 5 goto connect_failed
"%ADB_PATH%" disconnect %WATCH_IP% >nul 2>&1
"%ADB_PATH%" connect %WATCH_IP%
ping 127.0.0.1 -n 4 >nul
set "DEV_STATUS="
for /f "tokens=2" %%a in ('"%ADB_PATH%" devices 2^>^&1 ^| findstr /C:"%WATCH_IP%"') do set "DEV_STATUS=%%a"
if "%DEV_STATUS%"=="device" goto connect_ok
set /a ATTEMPT+=1
if %ATTEMPT% LEQ 5 echo [THU %ATTEMPT%/5] Thiet bi van offline/ko tim thay - thu lai sau 3s...
goto retry_loop
:connect_failed
echo [LOI] Khong ket noi duoc sau 5 lan thu!
echo Kiem tra tren dien thoai: SmartThings / More options / Developer options / ADB debugging.
goto show_devices
:connect_ok
echo Ket noi thanh cong! %WATCH_IP% dang o trang thai device.
:show_devices
echo.
echo Thiet bi adb hien tai:
"%ADB_PATH%" devices
pause
