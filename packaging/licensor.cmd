@echo off
setlocal EnableExtensions

set "DIR=%~dp0"
if exist "%DIR%licensor.exe" (
  "%DIR%licensor.exe" %*
  exit /b %ERRORLEVEL%
)

if exist "%DIR%jre\bin\java.exe" (
  "%DIR%jre\bin\java.exe" %JAVA_OPTS% -jar "%DIR%licensor.jar" %*
  exit /b %ERRORLEVEL%
)

where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
  echo licensor: Java not found. Install Java 21 or use a bundle that includes jre\.
  exit /b 127
)

java %JAVA_OPTS% -jar "%DIR%licensor.jar" %*
exit /b %ERRORLEVEL%
