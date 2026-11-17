# TeX Live để biên dịch báo cáo (XeLaTeX + biber), không cần cài TeX lên máy.
# Dùng qua bash Report/build.sh. Xóa khi không cần: docker rmi shopflash-latex:1
# Ghim theo digest (bản latest-small, TeX Live 2026) để cả nhóm biên dịch ra cùng một kết quả.
FROM texlive/texlive:latest-small@sha256:c572bf2d14631545769e5a8f8d315e7b8f8896b14720b1c7a063917d503fd46a

# Các gói main.tex và phần nội dung của từng người cần mà bản small chưa có.
RUN tlmgr install \
      biber biblatex biblatex-ieee logreq csquotes \
      koma-script titlesec fancyhdr setspace enumitem multirow caption \
      booktabs colortbl float pdfpages eso-pic pgf pgfplots listings \
      fvextra fancyvrb upquote lineno xurl tex-gyre lm \
 && cp "$(kpsewhich -var-value TEXMFSYSVAR)/fonts/conf/texlive-fontconfig.conf" /etc/fonts/conf.d/09-texlive.conf \
 && fc-cache -f

WORKDIR /work
