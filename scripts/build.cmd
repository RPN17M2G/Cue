@echo off
setlocal

if not defined JAVA_HOME (
  echo JAVA_HOME must point at a JDK 17 or newer ^(the project builds with 21^). 1>&2
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0.."

if not exist local.properties (
  echo sdk.dir=%LOCALAPPDATA:\=/%/Android/Sdk> local.properties
)

set "ARGS_FILE=%TEMP%\cue-build-args.txt"
set "ARGS="
if exist "%ARGS_FILE%" set /p ARGS=<"%ARGS_FILE%"
if "%ARGS%"=="" set "ARGS=assembleDebug testDebugUnitTest"

del /q "%TEMP%\cue-gradle.exit" 2>nul
call gradlew.bat %ARGS% --console=plain > "%TEMP%\cue-gradle.log" 2>&1
echo %ERRORLEVEL%> "%TEMP%\cue-gradle.exit"
endlocal
