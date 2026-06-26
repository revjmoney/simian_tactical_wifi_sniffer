@echo off
REM ---- push simian_tactical_wifi_sniffer to GitHub (repo already created on gh) ----
cd /d D:\smsnug\simian_tactical_wifi_sniffer

REM add launcher icons so the apps build
if not exist "sniffer\res\drawable" mkdir "sniffer\res\drawable"
if not exist "server\res\drawable" mkdir "server\res\drawable"
copy /y "D:\smsnug\reconcapture\res\drawable\ic_launcher.png" "sniffer\res\drawable\" >nul
copy /y "D:\smsnug\reconserver\res\drawable\ic_launcher.png"  "server\res\drawable\" >nul

git init
git config user.name "revjmoney"
git config user.email "myemail937@gmail.com"
git add .
git commit -m "Initial release: setchan WLC_SET_CHANNEL ioctl + Simian Tactical WiFi Sniffer & Server"
git branch -M main
git remote add origin https://github.com/revjmoney/simian_tactical_wifi_sniffer.git 2>nul
git remote set-url origin https://github.com/revjmoney/simian_tactical_wifi_sniffer.git
git push -u origin main

echo.
echo ============================================================
echo  pushed -^> https://github.com/revjmoney/simian_tactical_wifi_sniffer
echo ============================================================
