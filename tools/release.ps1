<#
.SYNOPSIS
  Build, tandatangani, dan terbitkan rilis TermUL ke GitHub Releases (update lewat menu, ADR 0003).

.DESCRIPTION
  1. Build app-image/msi (profile package-win) + zip JAR portable (package-jar).
  2. ReleaseTool: manifest.json + manifest.json.sig (Ed25519) + semua jar di app\target\release.
  3. gh release create v<versi> dengan aset tersebut (+ zip portable dan .msi untuk instalasi baru).

  Butuh: JDK 25 di PATH/JAVA_HOME, GitHub CLI (winget install GitHub.cli; gh auth login),
  private key di ~\.termul-release\update-signing.key (atau env TERMUL_UPDATE_KEY).

.EXAMPLE
  .\tools\release.ps1 -NotesFile notes.txt -Msi
  .\tools\release.ps1 -NotesFile notes.txt -Draft   # periksa dulu di GitHub, lalu Publish manual
#>
param(
    [Parameter(Mandatory = $true)][string]$NotesFile,
    [switch]$Msi,
    [switch]$Draft,
    [switch]$AllowDirty
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Fail([string]$message) {
    Write-Host "GAGAL: $message" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $NotesFile)) { Fail "File catatan rilis tidak ada: $NotesFile" }
$NotesFile = (Resolve-Path $NotesFile).Path
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Fail "GitHub CLI (gh) belum terpasang: winget install GitHub.cli, lalu gh auth login"
}

# Versi = jumlah commit: rilis harus dari commit yang bersih dan sudah di-push
$dirty = git status --porcelain
if ($dirty -and -not $AllowDirty) { Fail "Working tree belum bersih. Commit dulu (atau -AllowDirty untuk uji)." }
$commit = (git rev-parse HEAD).Trim()
$remoteBranches = git branch -r --contains $commit
if (-not $remoteBranches) { Fail "Commit $commit belum ada di remote. git push dulu." }
$branch = (git rev-parse --abbrev-ref HEAD).Trim()
if ($branch -ne 'master') {
    Write-Host "Peringatan: rilis dari branch '$branch', bukan master. Versi (jumlah commit) bisa lebih kecil dari rilis sebelumnya dan ditolak sebagai downgrade." -ForegroundColor Yellow
}

$mvnArgs = @('-Ppackage-win,package-jar', '-DskipTests', 'package')
if ($Msi) { $mvnArgs += '-Djpackage.type=msi' }
& "$root\mvnw.cmd" @mvnArgs
if ($LASTEXITCODE -ne 0) { Fail "Build Maven gagal" }

$inputDir = Join-Path $root 'app\target\jpackage-input'
$out = Join-Path $root 'app\target\release'
if (Test-Path $out) { Remove-Item -Recurse -Force $out -Confirm:$false }
$toolArgs = @('-cp', "$inputDir\libs\*", 'dev.egateza.termul.update.release.ReleaseTool', 'publish',
    '--input', $inputDir, '--out', $out, '--notes', $NotesFile)
if ($AllowDirty) { $toolArgs += '--allow-dirty' }
& java @toolArgs
if ($LASTEXITCODE -ne 0) { Fail "ReleaseTool gagal" }

$version = (Get-Content (Join-Path $out 'manifest.json') -Raw | ConvertFrom-Json).version
$tag = "v$version"
$assets = @(Get-ChildItem $out -File | ForEach-Object { $_.FullName })
$dist = Join-Path $root 'app\target\dist'
$appImage = Join-Path $dist 'TermUL'
if (-not $Msi -and (Test-Path $appImage)) {
    # instalasi baru tanpa WiX: folder app-image di-zip (ekstrak lalu jalankan TermUL.exe)
    $zip = Join-Path $dist "TermUL-$version-windows.zip"
    if (Test-Path $zip) { Remove-Item -Force $zip -Confirm:$false }
    Compress-Archive -Path $appImage -DestinationPath $zip
}
foreach ($extra in @("TermUL-$version-windows.zip", "TermUL-$version-jar.zip", "TermUL-$version.msi")) {
    $path = Join-Path $dist $extra
    if (Test-Path $path) { $assets += $path }
}

Write-Host "Menerbitkan $tag ($($assets.Count) aset) dari commit $commit"
$ghArgs = @('release', 'create', $tag) + $assets + @('--title', "TermUL $version", '--notes-file', $NotesFile,
    '--target', $commit)
if ($Draft) { $ghArgs += '--draft' }
& gh @ghArgs
if ($LASTEXITCODE -ne 0) { Fail "gh release create gagal" }
Write-Host "Selesai: $tag" -ForegroundColor Green
