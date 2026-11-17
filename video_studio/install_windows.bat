@echo off
setlocal
cd /d "%~dp0"
echo ==========================================
echo   PREET SPORTS AI CRICKET VIDEO STUDIO
echo   Windows local installer
echo ==========================================
where py >nul 2>nul
if errorlevel 1 (echo Python 3.11+ is required. Install from https://www.python.org/downloads/windows/ with Add Python to PATH selected.& pause& exit /b 1)
py -3.11 --version >nul 2>nul
if errorlevel 1 (echo Python 3.11 was not found. Install Python 3.11 or newer, then rerun.& pause& exit /b 1)
if not exist .venv py -3.11 -m venv .venv
call .venv\Scripts\activate.bat
python -m pip install --upgrade pip
pip install -r requirements.txt
if errorlevel 1 (echo Dependency installation failed.& pause& exit /b 1)
if not exist .env copy .env.example .env
if not exist output mkdir output
if not exist data mkdir data
where ffmpeg >nul 2>nul
if errorlevel 1 echo WARNING: FFmpeg not found. Install FFmpeg and add its bin folder to PATH before rendering.
python -c "from app.job_store import connect; connect().close(); print('Local database initialized.')"
echo.
echo Installation complete. Edit .env to add your Gemini API key, then run start_studio.bat.
pause
