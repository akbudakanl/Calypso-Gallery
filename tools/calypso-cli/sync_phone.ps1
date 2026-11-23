param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('pull', 'push')]
    [string]$Action,
    [string]$LocalRoot
)

# Minimal Windows MTP bridge sync using the Shell COM interface. The CLI itself
# never speaks MTP; this helper only copies whole folders before/after encryption.
$ErrorActionPreference = 'Stop'

try {
    $shell = New-Object -ComObject Shell.Application
    $thisPc = $shell.NameSpace(17)
    if ($null -eq $thisPc) { exit 2 }

    $phone = $null
    foreach ($item in $thisPc.Items()) {
        if ($item.Name -match 'phone|android|telefon|galaxy|pixel|mobil') {
            $phone = $item
            break
        }
    }
    if ($null -eq $phone) { exit 2 }

    $cliFolder = $null
    $queue = New-Object System.Collections.Queue
    $queue.Enqueue($phone.GetFolder)
    while ($queue.Count -gt 0 -and $null -eq $cliFolder) {
        $folder = $queue.Dequeue()
        foreach ($child in $folder.Items()) {
            if ($child.IsFolder -and $child.Name -eq 'CLI') {
                $parentName = $folder.Self.Name
                if ($parentName -eq 'Calypso Gallery') {
                    $cliFolder = $child.GetFolder
                    break
                }
            }
            if ($child.IsFolder) { $queue.Enqueue($child.GetFolder) }
        }
    }
    if ($null -eq $cliFolder) { exit 3 }

    if ($Action -eq 'pull') {
        if (-not (Test-Path -LiteralPath $LocalRoot)) {
            New-Item -ItemType Directory -Path $LocalRoot -Force | Out-Null
        }
        foreach ($bridge in $cliFolder.Items()) {
            if ($bridge.IsFolder) {
                $dest = Join-Path $LocalRoot $bridge.Name
                if (-not (Test-Path -LiteralPath $dest)) {
                    New-Item -ItemType Directory -Path $dest -Force | Out-Null
                }
                $shell.NameSpace($dest).CopyHere($bridge, 0x14)
            }
        }
    } else {
        if (-not (Test-Path -LiteralPath $LocalRoot)) { exit 4 }
        foreach ($bridgeDir in Get-ChildItem -LiteralPath $LocalRoot -Directory) {
            $existing = $null
            foreach ($item in $cliFolder.Items()) {
                if ($item.Name -eq $bridgeDir.Name) { $existing = $item; break }
            }
            if ($null -eq $existing) {
                $cliFolder.CopyHere($bridgeDir.FullName, 0x14)
            } else {
                foreach ($file in Get-ChildItem -LiteralPath $bridgeDir.FullName -Recurse -File) {
                    $relative = $file.FullName.Substring($bridgeDir.FullName.Length).TrimStart('\')
                    $targetFolder = $cliFolder
                    $parts = $relative.Split('\')
                    for ($i = 0; $i -lt $parts.Length - 1; $i++) {
                        $sub = $null
                        foreach ($item in $targetFolder.Items()) {
                            if ($item.Name -eq $parts[$i]) { $sub = $item; break }
                        }
                        if ($null -eq $sub) {
                            $targetFolder.CopyHere((Join-Path $bridgeDir.FullName ($parts[0..$i] -join '\')), 0x14)
                            Start-Sleep -Milliseconds 200
                        }
                        foreach ($item in $targetFolder.Items()) {
                            if ($item.Name -eq $parts[$i]) { $sub = $item; break }
                        }
                        if ($null -eq $sub) { throw "Cannot create folder $($parts[$i])" }
                        $targetFolder = $sub.GetFolder
                    }
                    $targetFolder.CopyHere($file.FullName, 0x14)
                }
            }
        }
    }
    exit 0
} catch {
    Write-Warning $_.Exception.Message
    exit 1
}
