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
set ATTEMPT=1
:retry_loop
if %ATTEMPT% GTR 5 goto connect_failed
set DEV_STATUS=
rem disconnect truoc de chan thiet bi kẹt ở trạng thái offline
"%ADB_PATH%" disconnect %WATCH_IP% >nul 2>&1
"%ADB_PATH%" connect %WATCH_IP% > "%TEMP%\adb_connect.log" 2>&1
timeout /t 3 /nobreak >nul
for /f "tokens=1,2" %%a in ('"%ADB_PATH%" devices 2^>^&1 ^| findstr /C:"%WATCH_IP%"') do set DEV_STATUS=%%b
if "%DEV_STATUS%"=="device" goto connect_ok
set /a ATTEMPT+=1
if %ATTEMPT% LEQ 5 echo [THU %ATTEMPT%/5] Thiet bi van offline/ko tim thay - thu lai sau 3s...
goto retry_loop
:connect_failed
echo [LOI] Khong ket noi duoc sau 5 lan thu!
echo Kiem tra: dien thoai > SmartThings > More options > Developer options > ADB debugging.
goto show_devices
:connect_ok
echo Ket noi thanh cong! Thiet bi %WATCH_IP% o trang thai device.
:show_devices
echo.
echo Thiet bi adb hien tai:
"%ADB_PATH%" devices
pause
