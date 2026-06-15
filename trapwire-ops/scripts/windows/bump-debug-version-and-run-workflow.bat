@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0bump-debug-version-and-run-workflow.ps1" %*
