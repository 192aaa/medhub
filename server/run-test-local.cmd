@echo off
REM Double-click friendly wrapper for the offline (no-Docker) backend test suite.
REM Forwards any extra args to run-test-local.ps1, e.g.:
REM   run-test-local.cmd -Reset
REM   run-test-local.cmd -Filter "AppointmentStatusConverterTest"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-test-local.ps1" %*
