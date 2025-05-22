@echo off
	cls
	mode con: cols=58 lines=37
	echo.
	echo %CYAN%  = -------------------------------------------------- = %RESET%
	echo.
	echo      About the "Aggressive" Profile
	echo.
	echo %CYAN%  = -------------------------------------------------- = %RESET%
	echo.
	echo      This profile scans all installed packages on your
	echo      Android device and tries to force-stop everything;
	echo      yes, even the ones not currently running.
	echo      The goal? 
	echo      To make sure nearly every app is forced to use
	echo      the selected graphics API.
	echo.
	echo      But heads-up, this method is brutal. Potential  
	echo      side effects include:
	echo      - Breaking WiFi Calling / VoLTE
	echo      - Resetting your default browser and keyboard
	echo      - Some apps just refusing to run under the 
	echo      selected API
	echo      - Possibly other weirdness we haven't documented 
	echo      yet
	echo.
	echo      Unless you know what you're doing, stick with
	echo      the "Normal" profile. 
	echo.
	echo      Would you like to visit GAMA's GitHub repository
	echo      to learn more? (Y/N)
	echo      (Either way, you'll be sent back to the main 
	echo      menu)
	echo.
	echo %CYAN%  = -------------------------------------------------- = %RESET%
	echo.
	echo	  Enter your choice: 
	echo.
	echo %CYAN%  = -------------------------------------------------- = %RESET%
	choice /c YN /n >nul
	set aggressive_learn_more_choice=%ERRORLEVEL%
	if %aggressive_learn_more_choice%==1 (
		start https://github.com/popovicialinc/gama?tab=readme-ov-file#%EF%B8%8Fknown-issues
		call "..\GAMA.bat" >nul 2>&1
		goto :eof
	)
	if %aggressive_learn_more_choice%==2 (
		call "..\GAMA.bat" >nul 2>&1
		goto :eof
	)