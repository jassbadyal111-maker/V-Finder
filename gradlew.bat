@echo off
setlocal
set "APP_HOME=%~dp0"
set "GRADLE_VERSION=8.10.2"
set "DIST_NAME=gradle-%GRADLE_VERSION%-bin"
set "DIST_ZIP=%APP_HOME%.gradle\%DIST_NAME%.zip"
set "DIST_DIR=%APP_HOME%.gradle\%DIST_NAME%"
set "GRADLE_BIN=%DIST_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat"

if exist "%GRADLE_BIN%" goto runGradle
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 goto systemGradle

if not exist "%APP_HOME%.gradle" mkdir "%APP_HOME%.gradle"
if not exist "%DIST_ZIP%" (
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/%DIST_NAME%.zip' -OutFile '%DIST_ZIP%'"
  if errorlevel 1 exit /b 1
)

if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%DIST_ZIP%' -DestinationPath '%APP_HOME%.gradle\%DIST_NAME%' -Force"
if errorlevel 1 exit /b 1

goto runGradle

:systemGradle
gradle %*
exit /b %ERRORLEVEL%

:runGradle
call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
