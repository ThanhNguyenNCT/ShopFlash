# latexmk cho báo cáo: XeLaTeX và biber. Thư mục ra (build/) do build.ps1, build.sh truyền vào.
$pdf_mode = 5;
$bibtex_use = 2;
$xelatex = 'xelatex -interaction=nonstopmode -file-line-error -synctex=1 %O %S';
