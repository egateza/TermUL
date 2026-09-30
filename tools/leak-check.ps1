# Cek kebocoran thread/koneksi TermUL (acceptance Fase 1).
# Pakai: buka beberapa tab, tutup semua (aplikasi tetap terbuka), tunggu > 30 detik, lalu:
#   powershell -ExecutionPolicy Bypass -File tools\leak-check.ps1 [-TermulHome D:\tmp\myterm-dev]
param(
    [string]$TermulHome = "D:\tmp\myterm-dev",
    [int]$LogLines = 400
)

$proc = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like '*TermULApp*' -or $_.CommandLine -like '*termul.home*' -or $_.CommandLine -like '*termul-app*' } |
    Select-Object -First 1
if (-not $proc) {
    Write-Host "Proses TermUL tidak ditemukan. Jalankan aplikasinya dulu." -ForegroundColor Red
    exit 2
}
Write-Host "TermUL pid $($proc.ProcessId)"

$jcmd = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\jcmd.exe" } else { "jcmd" }
$dump = & $jcmd $proc.ProcessId Thread.print 2>&1 | Out-String
$threads = [regex]::Matches($dump, '(?m)^"([^"]+)"') | ForEach-Object { $_.Groups[1].Value }

$suspects = [ordered]@{
    'term-writer-*'   = @($threads | Where-Object { $_ -like 'term-writer-*' })
    'sshd-SshClient*' = @($threads | Where-Object { $_ -like 'sshd-SshClient*' })
    'sftp-*'          = @($threads | Where-Object { $_ -like 'sftp-*' })
}
Write-Host "`nTotal thread: $($threads.Count)"
$leak = $false
foreach ($name in $suspects.Keys) {
    $n = $suspects[$name].Count
    # thread NIO MINA boleh tetap ada (SshClient singleton); writer terminal & sftp tidak boleh
    $bad = $n -gt 0 -and $name -ne 'sshd-SshClient*'
    if ($bad) { $leak = $true }
    Write-Host ("{0,-18} {1,3}  {2}" -f $name, $n, $(if ($bad) { 'BOCOR?' } else { 'ok' })) -ForegroundColor $(if ($bad) { 'Red' } else { 'Green' })
}

$log = Join-Path $TermulHome "config\logs\termul.log"
if (Test-Path $log) {
    $tail = Get-Content $log -Tail $LogLines
    $released = $tail | Select-String 'dilepas \(sisa: (\d+)\)' | Select-Object -Last 1
    $closed = @($tail | Select-String 'Menutup koneksi')
    Write-Host "`nLog terakhir pemakai dilepas: $(if ($released) { $released.Line.Trim() } else { '-' })"
    Write-Host "Baris 'Menutup koneksi' di $LogLines baris terakhir: $($closed.Count)"
    if ($released -and $released.Matches[0].Groups[1].Value -ne '0') { $leak = $true }
} else {
    Write-Host "Log $log tidak ditemukan (cek -TermulHome)." -ForegroundColor Yellow
}

if ($leak) {
    Write-Host "`nADA indikasi bocor. Simpan dump: jcmd $($proc.ProcessId) Thread.print > dump.txt" -ForegroundColor Red
    exit 1
}
Write-Host "`nTidak ada thread terminal/SFTP yang tertinggal." -ForegroundColor Green
