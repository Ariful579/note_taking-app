@echo off
title Taking Your Notes Tablet Server
echo ========================================================
echo   Starting Taking Your Notes App for Tablet
echo ========================================================
echo.
echo Open this URL on your tablet's Chrome browser:
echo   http://10.223.63.183:8080/
echo.
echo (Ensure both your laptop and tablet are on the same Wi-Fi)
echo.
echo Press Ctrl+C in this window when you want to stop the server.
echo ========================================================
echo.
python -m http.server 8080 --directory web_preview
pause
