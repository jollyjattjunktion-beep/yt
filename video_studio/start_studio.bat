@echo off
setlocal
cd /d "%~dp0"
if not exist .venv\Scripts\python.exe (echo Run install_windows.bat first.& pause& exit /b 1)
start "Preet Sports Worker" cmd /k "cd /d "%~dp0" ^&^& .venv\Scripts\python.exe -m app.worker"
timeout /t 2 >nul
start "Preet Sports Studio" cmd /k "cd /d "%~dp0" ^&^& .venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8000"
timeout /t 3 >nul
start "" http://127.0.0.1:8000
