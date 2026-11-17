# latexmk cho báo cáo: XeLaTeX và biber. Dùng -outdir=build để đặt file kết quả.
$pdf_mode = 5;
$bibtex_use = 2;
$xelatex = 'xelatex -interaction=nonstopmode -file-line-error -synctex=1 %O %S';
