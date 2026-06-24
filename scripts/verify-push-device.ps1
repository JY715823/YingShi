param(
    [string]$ApiBaseUrl = "http://10.106.3.193:8080/",
    [switch]$SkipBuild,
    [switch]$WatchLogcat
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$packageName = "com.example.yingshi"
$knownAdb = "E:\Soft\Android Studio SDK\platform-tools\adb.exe"
$adbCommand = Get-Command "adb" -ErrorAction SilentlyContinue
if (-not $adbCommand -and (Test-Path $knownAdb)) {
    $adb = $knownAdb
} elseif ($adbCommand) {
    $adb = $adbCommand.Source
} else {
    throw "adb.exe is missing. Install Android platform-tools or add adb.exe to PATH."
}

function Invoke-Step($Title, [scriptblock]$Action) {
    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
    & $Action
}

Invoke-Step "ADB devices" {
    & $adb devices
}

Invoke-Step "Firebase config check" {
    if (Test-Path "app\google-services.json") {
        Write-Host "[OK] app\google-services.json exists" -ForegroundColor Green
    } else {
        Write-Host "[MISSING] app\google-services.json is missing; Firebase token registration will not work." -ForegroundColor Red
    }
}

if (-not $SkipBuild) {
    Invoke-Step "Build debug APK" {
        $env:YINGSHI_DEBUG_API_BASE_URL = $ApiBaseUrl
        Write-Host "Using API base URL: $env:YINGSHI_DEBUG_API_BASE_URL"
        .\gradlew.bat :app:assembleDebug
    }
}

Invoke-Step "Install debug APK" {
    & $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
    & $adb shell am force-stop $packageName
}

Invoke-Step "Grant notification permission" {
    & $adb shell pm grant $packageName android.permission.POST_NOTIFICATIONS 2>$null
    & $adb shell appops set $packageName POST_NOTIFICATION allow 2>$null
    & $adb shell cmd notification set_dnd off 2>$null
}

Invoke-Step "Launch app" {
    & $adb shell monkey -p $packageName -c android.intent.category.LAUNCHER 1
}

Invoke-Step "Permission/device state" {
    & $adb shell dumpsys package $packageName | Select-String -Pattern "POST_NOTIFICATIONS|granted=true|granted=false" -CaseSensitive:$false
    & $adb shell appops get $packageName POST_NOTIFICATION 2>$null
    & $adb shell cmd notification get app $packageName 2>$null
    & $adb shell dumpsys notification --noredact | Select-String -Pattern "$packageName|yingshi_shared_updates|importance|blocked" -CaseSensitive:$false
}

Write-Host ""
Write-Host "Now open app Settings -> 权限与通知. Check 推送设备注册 and 最近推送诊断." -ForegroundColor Yellow
Write-Host "Trigger a comment/upload from the other account, then rerun server script or tap 最近推送诊断." -ForegroundColor Yellow

if ($WatchLogcat) {
    Write-Host ""
    Write-Host "Watching push-related logcat. Press Ctrl+C to stop." -ForegroundColor Yellow
    & $adb logcat -c
    & $adb logcat | Select-String -Pattern "PushTokenRegistrar|YingShiFirebaseMsg|PushNotificationPresenter|NotificationFallback|SyncVersionTracker|FirebaseMessaging|Notification|FCM|push" -CaseSensitive:$false
}
