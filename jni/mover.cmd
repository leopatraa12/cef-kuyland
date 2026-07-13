@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "SRC=%SCRIPT_DIR%libs\armeabi-v7a\libSAMP.so"
set "DEST=%SCRIPT_DIR%..\app\src\main\jniLibs\armeabi-v7a\"

if not exist "%SRC%" (
    echo Arquivo nao encontrado: "%SRC%"
    pause
    exit /b 1
)

if not exist "%DEST%" mkdir "%DEST%"

echo Copiando "%SRC%" para "%DEST%"
copy /Y "%SRC%" "%DEST%"

if errorlevel 1 (
    echo Falha ao copiar a lib.
    pause
    exit /b 1
)

echo Concluido.
pause
