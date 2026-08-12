@echo off

git rev-parse --is-inside-work-tree >nul 2>&1
if errorlevel 1 (
  echo Run this script inside a Git repository.
  exit /b 1
)

for /f "delims=" %%i in ('git rev-parse --show-toplevel') do set ROOT_DIR=%%i
cd /d "%ROOT_DIR%"

git config --local commit.template .gitmessage.txt
git config --local core.hooksPath .githooks

echo Local Git settings have been configured.
for /f "delims=" %%i in ('git config --local --get commit.template') do echo commit.template=%%i
for /f "delims=" %%i in ('git config --local --get core.hooksPath') do echo core.hooksPath=%%i
echo Commit message example: feat: MID4-12 add user signup
