@echo off
ren "%userprofile%\Zomboid\Workshop" "older-Workshop"
mklink /J "%userprofile%\Zomboid\Workshop" "%~dp0"
pause