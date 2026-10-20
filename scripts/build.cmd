@echo off
setlocal
set "JAVA_HOME=C:\Users\omerg\.cue-toolchain\jdk21\jdk-21.0.12.1+1"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0.."

if not exist local.properties (
  echo sdk.dir=C\:\\Users\\omerg\\AppData\\Local\\Android\\Sdk> local.properties
)

set "ARGS_FILE=%TEMP%\cue-build-args.txt"
set "ARGS="
if exist "%ARGS_FILE%" set /p ARGS=<"%ARGS_FILE%"
if "%ARGS%"=="" set "ARGS=assembleDebug testDebugUnitTest"

del /q "%TEMP%\cue-gradle.exit" 2>nul
call gradlew.bat %ARGS% --console=plain > "%TEMP%\cue-gradle.log" 2>&1
echo %ERRORLEVEL%> "%TEMP%\cue-gradle.exit"
endlocal
