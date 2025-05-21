@echo off
set CYAN=[96m
set RESET=[0m
color 0F
cls

echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%
echo.
echo %CYAN%     Launching all apps in 3 seconds... %RESET%
echo.
echo %CYAN%  = -------------------------------------------------- = %RESET%

timeout /t 5
cls
adb shell "for pkg in $(pm list packages | cut -f2 -d:); do monkey -p \"$pkg\" -c android.intent.category.LAUNCHER 1; done" 2>&1 | findstr /v "** No activities found to run"

if %ERRORLEVEL% neq 0 (
	color 0C
	echo.
	echo   = -------------------------------------------------- =
	echo.
	echo      Oh-oh. An error has occurred.
	echo      Visit GAMA's GitHub repository from the main
	echo      menu - You should find a solution there.
	echo.
	echo   = -------------------------------------------------- =
	echo.
) else (
	color 0A
	echo.
	echo   = -------------------------------------------------- =
	echo.
	echo      All apps launched successfully! 
	echo      Clear them using the Recents menu - "Close all".
	echo.
	echo   = -------------------------------------------------- =
	echo.
)

pause

goto :eof