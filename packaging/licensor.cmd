@echo off
setlocal
set DIR=%~dp0
java %JAVA_OPTS% -jar "%DIR%licensor.jar" %*
