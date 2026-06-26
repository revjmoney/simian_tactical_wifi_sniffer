@echo off
REM ---------------------------------------------------------------------------
REM  Build a Simian Tactical APK with NO Android Studio / NO Gradle.
REM  Pipeline: aapt -> javac -> d8 -> aapt add -> zipalign -> apksigner -> adb.
REM
REM  Usage:   build_apk.bat sniffer      (default)
REM           build_apk.bat server
REM
REM  >>> EDIT THESE PATHS for your machine <<<
REM ---------------------------------------------------------------------------
setlocal enabledelayedexpansion
set SDK=D:\android\Sdk
set BT=%SDK%\build-tools\34.0.0
set JAR=%SDK%\platforms\android-34\android.jar
set JDK=D:\slicktop\jdk17\jdk-17.0.19+10\bin
set ADB=D:\smsnug\adb.exe
set KS=%USERPROFILE%\.android\debug.keystore
set ROOT=%~dp0

set APP=%1
if "%APP%"=="" set APP=sniffer
if /I "%APP%"=="sniffer" ( set PKG=com\reconmonkey\capture& set NAME=SimianSniffer )
if /I "%APP%"=="server"  ( set PKG=com\reconmonkey\server& set NAME=SimianServer )
set P=%ROOT%%APP%

if exist "%P%\bin" rmdir /s /q "%P%\bin"
if exist "%P%\gen" rmdir /s /q "%P%\gen"
mkdir "%P%\bin\classes"
mkdir "%P%\gen"

echo [1/6] aapt (resources + R.java)...
"%BT%\aapt.exe" package -f -m -J "%P%\gen" -M "%P%\AndroidManifest.xml" -S "%P%\res" -I "%JAR%" -F "%P%\bin\resources.ap_" || goto :err

echo [2/6] javac...
"%JDK%\javac.exe" -encoding UTF-8 -source 8 -target 8 -nowarn -cp "%JAR%" -d "%P%\bin\classes" "%P%\gen\%PKG%\R.java" "%P%\src\%PKG%\MainActivity.java" || goto :err

echo [3/6] d8 (dex, min-api 19)...
set CL=
for %%f in ("%P%\bin\classes\%PKG%\*.class") do set CL=!CL! "%%f"
call "%BT%\d8.bat" --min-api 19 --lib "%JAR%" --output "%P%\bin" !CL! || goto :err

echo [4/6] package dex into apk...
copy /y "%P%\bin\resources.ap_" "%P%\bin\unsigned.apk" >nul
pushd "%P%\bin"
"%BT%\aapt.exe" add "unsigned.apk" "classes.dex" >nul || (popd & goto :err)

echo [5/6] zipalign + sign...
"%BT%\zipalign.exe" -f 4 "unsigned.apk" "aligned.apk" || (popd & goto :err)
call "%BT%\apksigner.bat" sign --ks "%KS%" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --min-sdk-version 19 --out "%NAME%.apk" "aligned.apk" || (popd & goto :err)
popd

echo [6/6] adb install...
"%ADB%" install -r "%P%\bin\%NAME%.apk"
echo.
echo === DONE: %NAME%.apk built + installed ===
goto :eof
:err
echo.
echo *** BUILD FAILED at the step above ***
