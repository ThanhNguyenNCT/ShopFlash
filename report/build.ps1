# Biên dịch báo cáo bằng Docker (Windows PowerShell). PDF nằm ở report\build\main.pdf.
#   powershell -ExecutionPolicy Bypass -File report\build.ps1        biên dịch
#   powershell -ExecutionPolicy Bypass -File report\build.ps1 -c     xóa file trung gian
# Lần đầu dựng image shopflash-latex:1 (tải khoảng 625 MB, chiếm khoảng 2,3 GB trên đĩa). Xóa image: docker rmi shopflash-latex:1
$ErrorActionPreference = 'Stop'
$Report = $PSScriptRoot
$Image = 'shopflash-latex:1'

docker image inspect $Image *> $null
if ($LASTEXITCODE -ne 0) {
  docker build -t $Image (Join-Path $Report 'docker')
  if ($LASTEXITCODE -ne 0) { exit 1 }
}

# Gắn font Times New Roman và Consolas của Windows (chỉ đọc) để PDF dùng đúng font quy định.
# Không có thì main.tex tự dùng TeX Gyre Termes và Latin Modern Mono.
$fonts = @()
foreach ($f in 'times.ttf', 'timesbd.ttf', 'timesi.ttf', 'timesbi.ttf', 'consola.ttf', 'consolab.ttf', 'consolai.ttf', 'consolaz.ttf') {
  $p = Join-Path $env:WINDIR "Fonts\$f"
  if (Test-Path $p) { $fonts += @('-v', "${p}:/usr/share/fonts/windows/${f}:ro") }
}

docker run --rm @fonts -v "${Report}:/work" -w /work $Image latexmk -outdir=build @args main.tex
exit $LASTEXITCODE
