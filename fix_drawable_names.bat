@echo off
cd /d "%~dp0app\src\main\res\drawable"
echo Current directory: %CD%

rem Step 1: Rename to temp name (needed on Windows for case-only renames)
if exist "Gesture.png" (
    rename "Gesture.png" "gesture_temp.png"
    echo Step 1: Renamed Gesture.png to gesture_temp.png
) else (
    echo Gesture.png not found, checking for gesture_temp.png...
)

rem Step 2: Rename temp to final lowercase name
if exist "gesture_temp.png" (
    rename "gesture_temp.png" "gesture.png"
    echo Step 2: Renamed gesture_temp.png to gesture.png
)

rem Confirm result
if exist "gesture.png" (
    echo SUCCESS: gesture.png is ready!
) else (
    echo ERROR: Something went wrong. Please rename manually.
)

echo.
echo Now do: Build ^> Clean Project ^> Rebuild in Android Studio
pause
