@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0borrar_sigf.ps1"
if errorlevel 1 (echo El restablecimiento no se completo.)
pause
