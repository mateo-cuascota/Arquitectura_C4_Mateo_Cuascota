@echo off
rem Doble clic para iniciar todo el sistema (ejecuta iniciar.ps1 sin cambiar la politica de PowerShell).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar.ps1"
pause
