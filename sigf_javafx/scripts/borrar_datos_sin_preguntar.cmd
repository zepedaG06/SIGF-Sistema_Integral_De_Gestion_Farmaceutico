@echo off
rem Compatibilidad: todo restablecimiento requiere confirmacion y administrador.
call "%~dp0borrar_sigf.cmd" %*
exit /b %ERRORLEVEL%
