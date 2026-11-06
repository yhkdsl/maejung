#!/bin/bash
# 사용법: tap.sh "버튼 텍스트" [대기초]
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
B=$($ADB shell cat /sdcard/ui.xml | grep -oE "text=\"$1\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]' | head -1)
read x1 y1 x2 y2 <<< $(echo $B | tr '[],' '   ')
$ADB shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 ))
sleep ${2:-3}
