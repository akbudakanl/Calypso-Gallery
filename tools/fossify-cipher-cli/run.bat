@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul 2>&1

REM ==============================================================================
REM Calypso Gallery - Windows Quick Launcher
REM ==============================================================================

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
cd /d "%SCRIPT_DIR%"

set "PYTHON_CMD="
set "VENV_PY=%SCRIPT_DIR%\.venv\Scripts\python.exe"

if exist "%VENV_PY%" (
    "%VENV_PY%" -c "import sys; sys.exit(0 if sys.version_info >= (3, 9) else 1)" >nul 2>&1
    if !errorlevel! equ 0 set PYTHON_CMD="%VENV_PY%"
)

if not defined PYTHON_CMD (
    where py >nul 2>&1
    if !errorlevel! equ 0 (
        py -3 -c "import sys; sys.exit(0 if sys.version_info >= (3, 9) else 1)" >nul 2>&1
        if !errorlevel! equ 0 set "PYTHON_CMD=py -3"
    )
)

if not defined PYTHON_CMD (
    where python >nul 2>&1
    if !errorlevel! equ 0 (
        python -c "import sys; sys.exit(0 if sys.version_info >= (3, 9) else 1)" >nul 2>&1
        if !errorlevel! equ 0 set "PYTHON_CMD=python"
    )
)

if not defined PYTHON_CMD (
    where python3 >nul 2>&1
    if !errorlevel! equ 0 (
        python3 -c "import sys; sys.exit(0 if sys.version_info >= (3, 9) else 1)" >nul 2>&1
        if !errorlevel! equ 0 set "PYTHON_CMD=python3"
    )
)

if not defined PYTHON_CMD (
    echo ==========================================================
    echo   [ERROR] Python 3.9+ was not found.
    echo ==========================================================
    echo Install Python 3 from https://www.python.org/downloads/
    echo ^(check "Add Python to PATH" during installation^)
    echo or run: winget install Python.Python.3.12
    echo.
    if "%~1"=="" pause
    exit /b 1
)

REM Check Python dependencies (cryptography, argon2, pillow)
%PYTHON_CMD% -c "import cryptography, argon2, PIL" >nul 2>&1
if errorlevel 1 (
    echo ==========================================================
    echo   [WARNING] Required Python libraries are missing.
    echo ==========================================================
    echo Will create a local virtual environment ^(.venv^) and install:
    echo   cryptography, argon2-cffi, pillow
    echo.
    set "DO_INSTALL=Y"
    set /p "DO_INSTALL=Create .venv and install dependencies now? [Y/n]: "
    if /i "!DO_INSTALL!"=="" set "DO_INSTALL=Y"
    if /i "!DO_INSTALL!"=="Y" goto :install_deps
    if /i "!DO_INSTALL!"=="YES" goto :install_deps
    if /i "!DO_INSTALL!"=="E" goto :install_deps
    if /i "!DO_INSTALL!"=="EVET" goto :install_deps
    echo Cannot proceed without required cryptography libraries.
    if "%~1"=="" pause
    exit /b 1
)
goto :run_cli

:install_deps
if not exist "%VENV_PY%" (
    %PYTHON_CMD% -m venv "%SCRIPT_DIR%\.venv"
    if errorlevel 1 (
        echo [ERROR] Failed to create virtual environment.
        if "%~1"=="" pause
        exit /b 1
    )
)
"%VENV_PY%" -m pip install -r "%SCRIPT_DIR%\requirements.txt"
if errorlevel 1 (
    echo [ERROR] Failed to install dependencies from requirements.txt.
    if "%~1"=="" pause
    exit /b 1
)
set PYTHON_CMD="%VENV_PY%"

:run_cli
set "HAS_ADB=0"
for %%A in (%*) do (
    if "%%~A"=="--adb" set "HAS_ADB=1"
)
if "%HAS_ADB%"=="1" (
    echo [*] --adb specified: running with ADB network access allowed.
) else (
    echo [Sandbox] Python network audit hook active ^(OS-level bwrap sandbox is Linux-only^).
)

%PYTHON_CMD% "%SCRIPT_DIR%\cipher_vault.py" %*
set "EXIT_CODE=%errorlevel%"

if "%~1"=="" (
    echo.
    pause
)
exit /b %EXIT_CODE%
