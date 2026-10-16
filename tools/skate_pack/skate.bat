@echo off
rem Opens the Skate engine in host mode (after GTA, before Minecraft), with the folders "cj_setup.py skate" saved.
if not "%CJ_SETUP%"=="" goto run
if exist "%~dp0..\cj-mod\setup\cj_setup.py" set "CJ_SETUP=%~dp0..\cj-mod\setup\cj_setup.py"
rem the base package unzipped beside this one, as cj-mod-<version>
if "%CJ_SETUP%"=="" for /d %%D in ("%~dp0..\cj-mod*") do if exist "%%D\setup\cj_setup.py" set "CJ_SETUP=%%D\setup\cj_setup.py"
:run
if not exist "%CJ_SETUP%" (
    echo cannot find cj_setup.py: put the skate pack next to the cj-mod folder, or set CJ_SETUP to its path
    pause
    exit /b 1
)
python "%CJ_SETUP%" play-skate
