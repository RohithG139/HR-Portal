#!/usr/bin/env bash
# .claude/hooks/desktop-notify.sh
# Fires when Claude finishes a task

# macOS
if command -v osascript &> /dev/null; then
  osascript -e 'display notification "Claude finished your task" with title "Claude Code"'

# Linux
elif command -v notify-send &> /dev/null; then
  notify-send "Claude Code" "Task complete"

# Windows (Git Bash / WSL — requires BurntToast module or falls back to msg.exe)
elif command -v powershell.exe &> /dev/null; then
  powershell.exe -NoProfile -NonInteractive -Command "
    try {
      Import-Module BurntToast -ErrorAction Stop
      New-BurntToastNotification -Text 'Claude Code', 'Task complete'
    } catch {
      msg * 'Claude Code - Task complete' 2>$null
    }
  "
fi

exit 0
