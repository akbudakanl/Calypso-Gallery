# export-pngs.ps1
# Renders Calypso Gallery Google Play phone screenshots (1080x1920) and GitHub README banner (1600x900)
# directly from docs/store-screenshots/index.html using local Microsoft Edge or Google Chrome headless.
#
# To swap photos: replace photo-1.jpg .. photo-6.jpg inside docs/store-screenshots/photos/
# and re-run this script.

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent (Split-Path -Parent $scriptDir)
$htmlPath = Join-Path $scriptDir "index.html"
$fileUri = ([System.Uri]$htmlPath).AbsoluteUri

$outDir = Join-Path $scriptDir "out"
$fastlaneDir = Join-Path $repoRoot "fastlane\metadata\android\en-US\images\phoneScreenshots"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
New-Item -ItemType Directory -Force -Path $fastlaneDir | Out-Null

$browserCandidates = @(
    "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    "C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    "C:\Program Files\Google\Chrome\Application\chrome.exe",
    "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
)
$browser = $browserCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $browser) {
    throw "Neither Microsoft Edge nor Google Chrome was found."
}

$tempProfile = Join-Path $env:TEMP ("calypso-ss-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $tempProfile | Out-Null

try {
    $jobs = @(
        @{ id = "slide-1"; w = 1080; h = 1920; out = (Join-Path $outDir "01-gallery.png");         fastlane = (Join-Path $fastlaneDir "1.png") },
        @{ id = "slide-2"; w = 1080; h = 1920; out = (Join-Path $outDir "02-vault.png");           fastlane = (Join-Path $fastlaneDir "2.png") },
        @{ id = "slide-3"; w = 1080; h = 1920; out = (Join-Path $outDir "03-virtual-albums.png");  fastlane = (Join-Path $fastlaneDir "3.png") },
        @{ id = "slide-4"; w = 1080; h = 1920; out = (Join-Path $outDir "04-viewer.png");          fastlane = (Join-Path $fastlaneDir "4.png") },
        @{ id = "slide-5"; w = 1080; h = 1920; out = (Join-Path $outDir "05-themes.png");          fastlane = (Join-Path $fastlaneDir "5.png") },
        @{ id = "slide-6"; w = 1080; h = 1920; out = (Join-Path $outDir "06-privacy.png");         fastlane = (Join-Path $fastlaneDir "6.png") },
        @{ id = "github-banner"; w = 1600; h = 900; out = (Join-Path $outDir "github-banner.png"); fastlane = $null }
    )

    foreach ($job in $jobs) {
        $url = "${fileUri}?export=$($job.id)"
        $args = @(
            "--headless=new",
            "--disable-gpu",
            "--hide-scrollbars",
            "--force-device-scale-factor=1",
            "--user-data-dir=`"$tempProfile`"",
            "--window-size=$($job.w),$($job.h)",
            "--screenshot=`"$($job.out)`"",
            "`"$url`""
        )
        $proc = Start-Process -FilePath $browser -ArgumentList $args -Wait -PassThru -NoNewWindow
        if ($proc.ExitCode -ne 0 -or -not (Test-Path $job.out)) {
            throw "Failed to render $($job.id) (exit code $($proc.ExitCode))"
        }
        if ($job.fastlane) {
            Copy-Item -Path $job.out -Destination $job.fastlane -Force
        }
        Write-Host "Exported $($job.out) ($($job.w)x$($job.h))"
    }
} finally {
    Remove-Item -Path $tempProfile -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "Done! Fastlane screenshots updated in $fastlaneDir" -ForegroundColor Green
