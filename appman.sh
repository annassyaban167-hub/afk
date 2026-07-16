#!/bin/bash
# App Manager for Termux - list, force-stop, clear recents

MODE="${1:-list}"
FILTER="${2:-}"

# ─── LIST INSTALLED APPS ───
if [ "$MODE" = "list" ]; then
  PREFIX="${FILTER:-com.}"
  echo "📱 Apps matching: $PREFIX"
  echo "──────────────────────────────"
  pm list packages "$PREFIX" 2>/dev/null | sort | while read -r line; do
    pkg="${line#package:}"
    # Try to get app name
    name=""
    name=$(dumpsys package "$pkg" 2>/dev/null | grep "applicationInfo" | head -1 | grep -oP "labelRes=0x[0-9a-f]+" || true)
    [ -z "$name" ] && name="?"
    echo "  $pkg"
  done
  echo ""
  echo "Usage: $0 list <prefix>     — filter by prefix (default: com.)"
  echo "       $0 force <pkg>        — force-stop an app"
  echo "       $0 recents            — show recent apps (compact)"
  echo "       $0 killrecent <num>   — remove recents entry by number"
fi

# ─── FORCE STOP ───
if [ "$MODE" = "force" ]; then
  PKG="$FILTER"
  if [ -z "$PKG" ]; then
    echo "Usage: $0 force <package.name>"
    exit 1
  fi
  echo "🛑 Force-stopping: $PKG"
  am force-stop "$PKG" 2>&1
  echo "   result=$(am force-stop "$PKG" 2>&1 || true)"
  # Also try to remove from recents
  TASK=$(dumpsys activity recents 2>/dev/null | grep -B2 "$PKG" | grep "taskId" | grep -oP "taskId=\K[0-9]+" | head -1)
  if [ -n "$TASK" ]; then
    service call activity 79 i32 "$TASK" 2>/dev/null || true
    echo "   Removed task #$TASK from recents"
  fi
  echo "✅ Done"
fi

# ─── SHOW RECENTS (compact) ───
if [ "$MODE" = "recents" ]; then
  echo "🕐 Recent Apps (compact)"
  echo "──────────────────────────────"
  dumpsys activity recents 2>/dev/null | grep "RecentTaskInfo\|packageName\|id=" | head -40 | while read -r line; do
    # Extract package name or task ID
    pkg=$(echo "$line" | grep -oP "packageName=\K[^ }]+" || true)
    task=$(echo "$line" | grep -oP "id=\K[0-9]+" || true)
    [ -n "$pkg" ] && echo "  📱 $pkg"
    [ -n "$task" ] && echo "  #$task"
  done | uniq
  echo ""
  echo "To remove from recents: $0 killrecent <taskId>"
fi

# ─── KILL FROM RECENTS ───
if [ "$MODE" = "killrecent" ]; then
  TASK="$FILTER"
  if [ -z "$TASK" ]; then
    echo "Usage: $0 killrecent <taskId>"
    echo "Run '$0 recents' to find task IDs"
    exit 1
  fi
  echo "Removing task #$TASK from recents..."
  service call activity 79 i32 "$TASK" 2>/dev/null && echo "✅ Removed" || {
    service call activity 30 i32 "$TASK" 2>/dev/null && echo "✅ Removed" || echo "❌ Could not remove. Try: am stack remove $TASK"
  }
fi
