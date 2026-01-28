@echo off
set CYAN=[96m
set RESET=[0m
title Launch all apps...

color 0F
cls
mode con: cols=122 lines=27

echo.
echo %CYAN%  = ---------------------------------------------------------------------------------------------------------------- = %RESET%
echo.
echo %CYAN%                                       Launching all apps in 5 seconds... %RESET%
echo.
echo %CYAN%  = ---------------------------------------------------------------------------------------------------------------- = %RESET%

timeout /t 5
cls
"%~dp0\adb" shell "for pkg in $(pm list packages | cut -f2 -d:); do monkey -p \"$pkg\" -c android.intent.category.LAUNCHER 1; done" 2>&1 | findstr /v "** No activities found to run"

if %ERRORLEVEL% neq 0 (
	cls
	mode con: cols=122 lines=10
	color 0C
	echo.
	echo   = ---------------------------------------------------------------------------------------------------------------- =
	echo.
	echo                              Oh-oh. An error has occured. Are you sure your phone is plugged in?
	echo.
	echo   = ---------------------------------------------------------------------------------------------------------------- =
	echo.
) else (
	cls
	mode con: cols=122 lines=11
	color 0A
	echo.
	echo   = ---------------------------------------------------------------------------------------------------------------- =
	echo.
	echo                                              All apps launched successfully! 
	echo                                       Clear them using the Recents menu - "Close all"
	echo.
	echo   = ---------------------------------------------------------------------------------------------------------------- =
	echo.
)

pause

goto :eof