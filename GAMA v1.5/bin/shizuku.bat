:: ESSENTIALS
@echo off
setlocal EnableDelayedExpansion
title GAMA %version%
set CYAN=[96m
set RESET=[0m
mode con: cols=58 lines=34
cls

echo.
echo %CYAN%  = -------------------------------------------------- =%RESET%
echo.
echo       Starting Shizuku...
echo.
echo %CYAN%  = -------------------------------------------------- =%RESET%
echo.
"%~dp0\adb" shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
if %ERRORLEVEL%==0 (
	mode con: cols=58 lines=10
	cls
	color 0A
	echo.
	echo   = -------------------------------------------------- =
	echo.
	echo       Okay, Shizuku is now running.
	echo.
	echo   = -------------------------------------------------- =
	echo.
) else (
	color 0C
	echo.
	echo   = -------------------------------------------------- =
	echo.
	echo      Oh-oh. An error has occurred.
	echo.
	echo   = -------------------------------------------------- =
	echo.
)
pause
goto :eof
