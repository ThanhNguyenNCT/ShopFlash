<#
.SYNOPSIS
  Chạy toàn bộ ShopFlash trên máy dev (Windows): Oracle + Redis (Docker), backend Spring Boot, frontend Vite.

.DESCRIPTION
  Chạy từ bất kỳ đâu:  powershell -ExecutionPolicy Bypass -File scripts\dev.ps1
  Lần đầu sẽ tự tạo .env (mật khẩu ngẫu nhiên) nếu chưa có. Backend và frontend mở ở hai cửa sổ riêng;
  đóng cửa sổ là dừng. Dừng hẳn database: scripts\dev.ps1 -Stop

.PARAMETER NoBackend   Không chạy backend (ví dụ đang chạy backend trong IDE).
.PARAMETER NoFrontend  Không chạy frontend.
.PARAMETER Mock        Chỉ chạy frontend với dữ liệu mẫu (không cần Docker, không cần backend).
.PARAMETER Stop        Dừng container Oracle và Redis (dữ liệu vẫn giữ).
#>
param(
  [switch]$NoBackend,
  [switch]$NoFrontend,
  [switch]$Mock,
  [switch]$Stop
)

# Continue (không phải Stop): PowerShell 5.1 coi stderr của lệnh ngoài (docker, npm) là lỗi và dừng script.
# Mỗi bước tự kiểm tra $LASTEXITCODE.
$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Fail($msg) { Write-Host "LỖI: $msg" -ForegroundColor Red; exit 1 }
function Ok($msg) { Write-Host "    $msg" -ForegroundColor Green }
# Đường dẫn được nhúng vào lệnh '...' của cửa sổ mới: nhân đôi dấu nháy đơn để thư mục có dấu ' không làm hỏng lệnh.
function Quote($path) { $path -replace "'", "''" }
# Cổng đang có tiến trình khác giữ thì dừng, không tin nhầm backend/Vite cũ là cái vừa chạy.
function RequireFreePort($port, $what) {
  $listen = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($listen) {
    $proc = Get-Process -Id $listen.OwningProcess -ErrorAction SilentlyContinue
    Fail "Cổng $port đang bị chiếm bởi $($proc.ProcessName) (PID $($listen.OwningProcess)). Đóng $what cũ (hoặc: Stop-Process -Id $($listen.OwningProcess)) rồi chạy lại."
  }
}

if ($Stop) {
  Step 'Dừng Oracle và Redis (dữ liệu vẫn giữ trong volume)'
  docker compose stop
  exit $LASTEXITCODE
}

if ($Mock) {
  Step 'Frontend với dữ liệu mẫu: http://localhost:5173'
  Set-Location (Join-Path $Root 'frontend')
  if (-not (Test-Path 'node_modules')) { npm install }
  npm run dev:mock
  exit $LASTEXITCODE
}

# ---------- 1. Docker ----------
Step 'Kiểm tra Docker'
docker info --format '{{.ServerVersion}}' *> $null
if ($LASTEXITCODE -ne 0) { Fail 'Docker chưa chạy. Mở Docker Desktop rồi chạy lại.' }
Ok 'Docker đang chạy'

$images = (docker compose config --images) -split "`n" | Where-Object { $_ }
foreach ($img in $images) {
  docker image inspect $img *> $null
  if ($LASTEXITCODE -ne 0) {
    Fail "Thiếu image $img. Kéo về (một lần, Oracle khoảng 3,7 GB):`n    docker pull $img"
  }
}
Ok 'Đủ image Oracle và Redis'

# ---------- 2. .env ----------
if (-not (Test-Path '.env')) {
  Step 'Tạo .env với mật khẩu ngẫu nhiên (chỉ dùng trên máy này, không commit)'
  function NewPassword { 'Sf' + -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 18 | ForEach-Object { [char]$_ }) }
  $lines = (Get-Content '.env.example' -Encoding utf8) `
    -replace '^ORACLE_ADMIN_PASSWORD=.*', "ORACLE_ADMIN_PASSWORD=$(NewPassword)" `
    -replace '^ORACLE_APP_PASSWORD=.*', "ORACLE_APP_PASSWORD=$(NewPassword)" `
    -replace '^REDIS_PASSWORD=.*', "REDIS_PASSWORD=$(NewPassword)"
  # UTF-8 không BOM: BOM ở đầu file làm Docker Compose đọc sai dòng đầu.
  [IO.File]::WriteAllLines((Join-Path $Root '.env'), [string[]]$lines, (New-Object Text.UTF8Encoding $false))
  Ok '.env đã tạo'
}

# ---------- 3. Database ----------
Step 'Bật Oracle và Redis (lần đầu Oracle mất khoảng 2 phút)'
docker compose up -d --wait
if ($LASTEXITCODE -ne 0) { Fail 'docker compose up lỗi. Xem: docker compose logs oracle | Select-String shopflash' }
Ok 'Oracle và Redis healthy'

Step 'Tạo index Redis (init.lua)'
$init = docker compose exec -T redis redis-cli -e --eval /opt/shopflash/redis/init.lua 2>&1
if ($LASTEXITCODE -ne 0) { Fail "init.lua lỗi:`n$init`nNếu báo index cũ: làm theo lệnh FT.DROPINDEX trong thông báo rồi chạy lại." }
Ok "$init"

# ---------- 4. Backend ----------
if (-not $NoBackend) {
  Step 'Tìm JDK 21'
  $jdk = $null
  $candidates = @()
  if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
  $candidates += Get-ChildItem 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft' `
    -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'jdk-?(2[1-9]|[3-9]\d)' } | ForEach-Object FullName
  foreach ($c in $candidates) {
    $java = Join-Path $c 'bin\java.exe'
    if (Test-Path $java) {
      $v = (& $java -version 2>&1 | Select-Object -First 1) -replace '.*version "(\d+).*', '$1'
      if ([int]$v -ge 21) { $jdk = $c; break }
    }
  }
  if (-not $jdk) { Fail 'Không tìm thấy JDK 21 trở lên. Cài Temurin 21 (adoptium.net) hoặc đặt JAVA_HOME.' }
  Ok "JDK: $jdk"

  RequireFreePort 8080 'backend'
  Step 'Mở backend ở cửa sổ mới (profile dev: có dữ liệu mẫu)'
  $backendCmd = "`$env:JAVA_HOME='$(Quote $jdk)'; Set-Location '$(Quote $Root)\backend'; .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'"
  Start-Process powershell -ArgumentList '-NoExit', '-Command', $backendCmd | Out-Null

  Step 'Chờ backend sẵn sàng (GET /api/health)'
  $healthy = $false
  for ($i = 0; $i -lt 90; $i++) {
    try {
      $h = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/health' -TimeoutSec 3
      if ($h.status -eq 'UP') { $healthy = $true; break }
    } catch { }
    Start-Sleep -Seconds 2
  }
  if (-not $healthy) { Fail 'Backend chưa lên sau 3 phút. Xem lỗi trong cửa sổ backend.' }
  Ok 'Backend UP, Oracle và Redis kết nối được'

  # Module query đọc read model Redis; nếu còn trống thì dựng một lần (khoảng 20-35 s với 100k đơn).
  # API /api/admin/** cần JWT của ADMIN: đăng nhập bằng tài khoản mẫu của profile dev.
  try {
    $login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/auth/login' -ContentType 'application/json' `
      -Body '{"email":"admin@shopflash.local","password":"ShopFlash123!"}' -TimeoutSec 30
    $auth = @{ Authorization = "Bearer $($login.accessToken)" }
    $s = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/admin/reports/analytics/status' -Headers $auth -TimeoutSec 30
    if (-not $s.inSync) {
      Step "Dựng read model Redis cho báo cáo (Oracle $($s.oracle.countedOrders) đơn, Redis $($s.redis.orderDocs))"
      $r = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8080/api/admin/reports/analytics/rebuild' `
        -Headers $auth -ContentType 'application/json' -Body '{}' -TimeoutSec 600
      Ok "Đã dựng $($r.orders) đơn, $($r.lines) dòng hàng trong $([math]::Round($r.elapsedMs / 1000, 1)) s"
    }
  } catch {
    Write-Host "    Bỏ qua bước dựng read model: $($_.Exception.Message)" -ForegroundColor Yellow
  }
}

# ---------- 5. Frontend ----------
if (-not $NoFrontend) {
  RequireFreePort 5173 'Vite (frontend)'
  Step 'Mở frontend ở cửa sổ mới'
  $frontend = Join-Path $Root 'frontend'
  if (-not (Test-Path (Join-Path $frontend 'node_modules'))) {
    Step 'Cài thư viện frontend (lần đầu)'
    Push-Location $frontend; npm install; Pop-Location
  }
  Start-Process powershell -ArgumentList '-NoExit', '-Command', "Set-Location '$(Quote $frontend)'; npm run dev" | Out-Null
  Start-Sleep -Seconds 4
  Start-Process 'http://localhost:5173'
}

Step 'Xong'
Write-Host '    Cửa hàng:      http://localhost:5173'
Write-Host '    Quản trị:      http://localhost:5173/admin  (đăng nhập tạm: chọn vai trò Admin)'
Write-Host '    API health:    http://127.0.0.1:8080/api/health'
Write-Host '    Dừng database: scripts\dev.ps1 -Stop'
