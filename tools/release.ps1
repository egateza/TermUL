<#
.SYNOPSIS
  Build, tandatangani, dan terbitkan rilis TermUL ke GitHub Releases (update lewat menu, ADR 0003).

.DESCRIPTION
  Rilis penuh (default):
  1. Build app-image/msi (profile package-win) + zip JAR portable (package-jar).
  2. ReleaseTool: manifest.json + manifest.json.sig (Ed25519) + semua jar di app\target\release.
  3. gh release create v<versi> dengan aset tersebut (+ zip portable dan .msi untuk instalasi baru).

  -AssetsOnly: rilis v<versi> (juga draft) sudah dibuat dari mesin lain, mis. MacBook lewat tools/release-mac.sh.
  Hanya build zip app-image Windows (atau .msi dengan -Msi) lalu melampirkannya ke rilis itu. Tidak butuh private key
  dan tidak menyentuh manifest. HEAD wajib commit yang sama dengan commit rilis.

  Butuh: JDK 25 di PATH/JAVA_HOME, GitHub CLI (winget install GitHub.cli; gh auth login),
  private key di ~\.termul-release\update-signing.key (atau env TERMUL_UPDATE_KEY) untuk rilis penuh.
  Alur lintas OS (Windows + Mac dalam satu tag): docs/RELEASE-CROSS-PLATFORM.md.

.EXAMPLE
  .\tools\release.ps1 -NotesFile notes.txt -Msi
  .\tools\release.ps1 -NotesFile notes.txt -Draft   # periksa dulu di GitHub, lalu Publish manual
  .\tools\release.ps1 -AssetsOnly                   # lampirkan zip Windows ke rilis/draft yang dibuat dari Mac
#>
param(
    [string]$NotesFile,
    [switch]$Msi,
    [switch]$Draft,
    [switch]$AllowDirty,
    [switch]$AssetsOnly
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Fail([string]$message) {
    Write-Host "GAGAL: $message" -ForegroundColor Red
    exit 1
}

# Versi build dari build.properties di jar utama (sama dengan yang dibaca ReleaseTool)
function Get-BuildVersion {
    $inputDir = Join-Path $root 'app\target\jpackage-input'
    $jar = @(Get-ChildItem $inputDir -Filter 'termul-app-*.jar')
    if ($jar.Count -ne 1) { Fail "Harus ada tepat satu termul-app-*.jar di $inputDir" }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar[0].FullName)
    try {
        $entry = $zip.GetEntry('dev/egateza/termul/app/build.properties')
        if (-not $entry) { Fail "build.properties tidak ada di $($jar[0].Name)" }
        $reader = New-Object System.IO.StreamReader($entry.Open())
        try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally {
        $zip.Dispose()
    }
    $m = [regex]::Match($text, '(?m)^version=(\d+\.\d+\.\d+)\s*$')
    if (-not $m.Success) { Fail "Versi build tidak valid di build.properties" }
    return $m.Groups[1].Value
}

# Instalasi baru tanpa WiX: folder app-image di-zip (ekstrak lalu jalankan TermUL.exe)
function New-WindowsZip([string]$version) {
    $dist = Join-Path $root 'app\target\dist'
    $appImage = Join-Path $dist 'TermUL'
    if (-not (Test-Path $appImage)) { return $null }
    $zip = Join-Path $dist "TermUL-$version-windows.zip"
    if (Test-Path $zip) { Remove-Item -Force $zip -Confirm:$false }
    # tar.exe bawaan Windows 10+; Compress-Archive (PS 5.1) menulis path dengan backslash
    & "$env:SystemRoot\System32\tar.exe" -a -c -f $zip -C $dist TermUL
    if ($LASTEXITCODE -ne 0) { Fail "Gagal membuat $zip" }
    return $zip
}

if (-not $AssetsOnly) {
    if (-not $NotesFile) { Fail "-NotesFile wajib untuk rilis penuh (atau -AssetsOnly untuk melampirkan aset Windows)" }
    if (-not (Test-Path $NotesFile)) { Fail "File catatan rilis tidak ada: $NotesFile" }
    $NotesFile = (Resolve-Path $NotesFile).Path
}
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

if ($AssetsOnly) {
    $mvnArgs = @('-Ppackage-win', '-DskipTests', 'package')
    if ($Msi) { $mvnArgs += '-Djpackage.type=msi' }
    & "$root\mvnw.cmd" @mvnArgs
    if ($LASTEXITCODE -ne 0) { Fail "Build Maven gagal" }

    $version = Get-BuildVersion
    $tag = "v$version"
    # Tanpa redirect stderr (PS 5.1 + 'Stop' = NativeCommandError); pesan gh tampil apa adanya
    $target = gh release view $tag --json targetCommitish --jq .targetCommitish
    if ($LASTEXITCODE -ne 0) { Fail "Rilis $tag (juga draft) belum ada. Terbitkan dulu dari mesin lain, atau jalankan skrip ini tanpa -AssetsOnly." }
    $target = "$target".Trim()
    # Nomor versi = jumlah commit, jadi commit lain bisa bernomor sama: aset harus dari commit rilis
    if ($target -match '^[0-9a-f]{40}$') {
        $expected = $target
    } else {
        git fetch -q --tags origin
        $expected = git rev-parse --verify -q "$tag^{commit}"
    }
    if (-not $expected) { Fail "Commit rilis $tag tidak bisa ditentukan (target: $target)" }
    if ($expected.Trim() -ne $commit) {
        Fail "Rilis $tag dibuat dari commit $($expected.Substring(0, 7)), sedangkan HEAD $($commit.Substring(0, 7)). Jalankan: git checkout $($expected.Substring(0, 7))"
    }

    $assets = @()
    $dist = Join-Path $root 'app\target\dist'
    if ($Msi) {
        $msiFile = Join-Path $dist "TermUL-$version.msi"
        if (Test-Path $msiFile) { $assets += $msiFile }
    } else {
        $zip = New-WindowsZip $version
        if ($zip) { $assets += $zip }
    }
    if ($assets.Count -eq 0) { Fail "Aset Windows tidak terbentuk di $dist" }
    Write-Host "Melampirkan $($assets.Count) aset Windows ke $tag (commit $($commit.Substring(0, 7)) cocok)"
    $ghArgs = @('release', 'upload', $tag) + $assets + @('--clobber')
    & gh @ghArgs
    if ($LASTEXITCODE -ne 0) { Fail "gh release upload gagal" }
    Write-Host "Selesai: aset Windows terlampir di $tag" -ForegroundColor Green
    exit 0
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
# Tag sudah dibuat dari mesin lain (mis. Mac): jangan buat ulang manifest, cukup lampirkan aset Windows
$existing = @(gh release list --limit 100 --json tagName --jq '.[].tagName')
if ($existing -contains $tag) { Fail "Rilis $tag sudah ada (mungkin dibuat dari Mac). Untuk zip Windows saja: .\tools\release.ps1 -AssetsOnly" }
$assets = @(Get-ChildItem $out -File | ForEach-Object { $_.FullName })
$dist = Join-Path $root 'app\target\dist'
if (-not $Msi) {
    [void](New-WindowsZip $version)
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
Write-Host "Untuk DMG macOS di rilis yang sama: di Mac, git checkout $($commit.Substring(0, 7)) lalu tools/release-mac.sh --dmg-only"
