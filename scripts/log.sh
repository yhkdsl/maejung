#!/bin/bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
$ADB shell cat /sdcard/ui.xml | grep -oE 'text="[0-9]{2}:[0-9]{2}:[0-9]{2}[^"]*"' | sed -e 's/&#10;/\n/g' -e 's/^text="//' -e 's/"$//'
