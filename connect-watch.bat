@echo off
title Ket Noi Galaxy Watch
echo Dang ket noi toi Galaxy Watch tai 192.168.1.236:5555...
"C:\Users\ThanhDuy\Downloads\platform-tools-latest-windows\platform-tools\adb.exe" connect 192.168.1.236:5555
"C:\Users\ThanhDuy\Downloads\platform-tools-latest-windows\platform-tools\adb.exe" devices
echo.
echo Ket noi hoan tat!
pause
