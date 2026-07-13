@echo off
setlocal

echo Compiling libSAMP

set "SCRIPT_DIR=%~dp0"
set "SDK_NDK_ROOT=%LOCALAPPDATA%\Android\Sdk\ndk"
set "NDK_BUILD="

if defined ANDROID_NDK_HOME if exist "%ANDROID_NDK_HOME%\ndk-build.cmd" set "NDK_BUILD=%ANDROID_NDK_HOME%\ndk-build.cmd"
if not defined NDK_BUILD if defined ANDROID_NDK_ROOT if exist "%ANDROID_NDK_ROOT%\ndk-build.cmd" set "NDK_BUILD=%ANDROID_NDK_ROOT%\ndk-build.cmd"
if not defined NDK_BUILD if exist "%SDK_NDK_ROOT%\27.0.12077973\ndk-build.cmd" set "NDK_BUILD=%SDK_NDK_ROOT%\27.0.12077973\ndk-build.cmd"
if not defined NDK_BUILD if exist "%SDK_NDK_ROOT%\26.2.11394342\ndk-build.cmd" set "NDK_BUILD=%SDK_NDK_ROOT%\26.2.11394342\ndk-build.cmd"
if not defined NDK_BUILD if exist "%SDK_NDK_ROOT%\25.1.8937393\ndk-build.cmd" set "NDK_BUILD=%SDK_NDK_ROOT%\25.1.8937393\ndk-build.cmd"
if not defined NDK_BUILD if exist "%SDK_NDK_ROOT%\21.4.7075529\ndk-build.cmd" set "NDK_BUILD=%SDK_NDK_ROOT%\21.4.7075529\ndk-build.cmd"

if not defined NDK_BUILD (
    echo Nao encontrei o ndk-build.cmd.
    echo Define ANDROID_NDK_HOME ou instala o NDK em "%SDK_NDK_ROOT%".
    pause
    exit /b 1
)

echo Usando NDK: "%NDK_BUILD%"
pushd "%SCRIPT_DIR%"
call "%NDK_BUILD%"
set "BUILD_EXIT=%ERRORLEVEL%"
popd

if not "%BUILD_EXIT%"=="0" (
    echo Falha ao compilar libSAMP. Codigo: %BUILD_EXIT%
    pause
    exit /b %BUILD_EXIT%
)

echo Compilacao concluida.
pause
