@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0borrar_sigf.ps1" %*
set "SIGF_RESULT=%ERRORLEVEL%"
if not "%SIGF_RESULT%"=="0" echo El restablecimiento no se completo.
exit /b %SIGF_RESULT%
