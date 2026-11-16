#!/usr/bin/env python3
"""Convert the three supplied Transaction chapters without changing experiment data.

Requires markdown-it-py (Ubuntu: python3-markdown-it). Normal PDF builds only
read the generated .tex files and do not require this script or Python.
"""

import argparse
import json
import re
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

from markdown_it import MarkdownIt

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = ROOT / "Bao_cao_Transaction_3_1-3_3_Rut_gon"
PARTS = (
    ("3.1_Tong_quan_va_phuong_phap_nghien_cuu.md", "01-tong-quan.tex"),
    ("3.2_Co_so_ly_thuyet_va_thuc_nghiem_ACID.md", "02-thuc-nghiem-acid.tex"),
    ("3.3_Danh_gia_hieu_nang_va_lua_chon_DBMS (1).md", "03-hieu-nang.tex"),
)
MD = MarkdownIt("commonmark").enable("table")
CAPTION = re.compile(r"^\*(Bảng|Mã nguồn|Kết quả|Hình) 3\.(\d+)\. (.*?)\*\s*$", re.S)
FIGURE_PLACEHOLDER = re.compile(r"^\*\*\[Hình 3\.(\d+) — (.*?)\]\*\*$", re.S)
REFERENCE = re.compile(r"\b(Mục|Bảng|Mã nguồn|Kết quả|Hình) 3\.([\d.]*\d)(?!\d)")
KINDS = {"Mục": "sec", "Bảng": "tab", "Mã nguồn": "lst", "Kết quả": "result", "Hình": "fig"}


def label(kind, number):
    return f"{KINDS[kind]}:tx-{number.replace('.', '-')}"


def escape(value):
    substitutions = {
        "\\": r"\textbackslash{}", "&": r"\&", "%": r"\%",
        "$": r"\$", "#": r"\#", "_": r"\_", "{": r"\{",
        "}": r"\}", "~": r"\textasciitilde{}", "^": r"\textasciicircum{}",
        "→": r"\(\rightarrow\)", "×": r"\(\times\)", "≤": r"\(\leq\)",
        "≥": r"\(\geq\)", "±": r"\(\pm\)", "−": "-",
    }
    return "".join(substitutions.get(c, c) for c in value)


class Converter:
    def __init__(self):
        self.math = {}
        self.counts = Counter()
        self.labels = set()
        self.references = set()

    def protect_math(self, value):
        def protect(match):
            key = f"TXMATHPLACEHOLDER{len(self.math)}TOKEN"
            raw = match.group()
            self.math[key] = ("\\[" + raw[2:-2].strip() + "\\]"
                              if raw.startswith("$$") else "\\(" + raw[1:-1] + "\\)")
            return key
        # Fenced code must remain byte-for-byte unchanged.
        segments = re.split(r"(^```[^\n]*\n.*?^```\s*$)", value, flags=re.M | re.S)
        return "".join(s if s.startswith("```") else re.sub(
            r"\$\$.*?\$\$|(?<!\\)\$[^\n$]+(?<!\\)\$", protect, s, flags=re.S
        ) for s in segments)

    def text(self, value):
        result, start = [], 0
        for match in REFERENCE.finditer(value):
            result.append(escape(value[start:match.start()]))
            kind, number = match.groups()
            target = label(kind, number)
            self.references.add(target)
            result.append(escape(kind) + r"~\ref{" + target + "}")
            start = match.end()
        result.append(escape(value[start:]))
        value = "".join(result)
        for key, formula in self.math.items():
            value = value.replace(key, formula)
        return value

    def inline(self, tokens):
        result = []
        for t in tokens:
            if t.type == "text":
                result.append(self.text(t.content))
            elif t.type == "code_inline":
                # Permit breaks inside identifiers and paths in narrow table cells.
                code = "".join(escape(c) + (r"\allowbreak{}" if c in "_./:-=" else "")
                               for c in t.content)
                result.append(r"\texttt{" + code + "}")
            elif t.type in ("strong_open", "em_open"):
                result.append(r"\textbf{" if t.type == "strong_open" else r"\emph{")
            elif t.type in ("strong_close", "em_close"):
                result.append("}")
            elif t.type in ("softbreak", "hardbreak"):
                result.append(" " if t.type == "softbreak" else r"\\")
            elif t.type == "link_open":
                href = t.attrGet("href")
                if href.startswith("../report/"):
                    href = href[len("../report/"):]
                result.append(r"\href{" + escape(href) + "}{")
            elif t.type == "link_close":
                result.append("}")
            else:
                raise ValueError(f"Unsupported inline token: {t.type}")
        return "".join(result)

    def add_label(self, kind, number):
        name = label(kind, number)
        if name in self.labels:
            raise ValueError(f"Duplicate label: {name}")
        self.labels.add(name)
        self.counts[kind] += 1
        return r"\label{" + name + "}"

    def caption(self, tokens, offset, expected):
        if tokens[offset].type != "paragraph_open":
            raise ValueError("Expected a caption paragraph")
        match = CAPTION.fullmatch(tokens[offset + 1].content)
        if not match or match[1] not in expected:
            raise ValueError(f"Missing caption after {expected}: {tokens[offset + 1].content}")
        kind, number, body = match.groups()
        return kind, number, self.inline(MD.parseInline(body)[0].children)

    def table(self, tokens, i):
        end = next(j for j in range(i + 1, len(tokens)) if tokens[j].type == "table_close")
        rows, row, cells = [], [], []
        for t in tokens[i + 1:end]:
            if t.type == "tr_open":
                row = []
            elif t.type == "inline":
                row.append(self.inline(t.children))
                cells.append(t.content)
            elif t.type == "tr_close":
                rows.append(row)
        kind, number, caption = self.caption(tokens, end + 1, {"Bảng"})
        cols = len(rows[0])
        # Relative column widths, chosen for the supplied tables' actual content.
        widths = {
            "1": [0.65, 1.05, 1.3], "2": [0.4, 0.65, 1.95],
            "3": [1.25, 0.95, 0.9, 0.9], "4": [1.55, 0.5, 1.05, 0.9],
            "5": [0.85, 1.55, 0.6], "6": [1.4, 0.8, 0.8],
            "7": [1.15, 0.9, 0.95], "8": [0.8, 1.1, 1.1],
            "9": [1.4, 0.8, 0.8], "10": [0.9, 0.45, 1.65],
            "11": [0.7, 1.15, 1.15], "12": [0.8, 1.05, 0.85, 1.1, 1.1, 1.1],
            "13": [0.65, 0.85, 1.25, 1.25],
            "14": [0.65, 0.9, 1.1, 1.1, 1.15, 1.15, 0.95],
            "15": [0.65, 1.175, 1.175],
        }.get(number, [1] * cols)
        assert len(widths) == cols and abs(sum(widths) - cols) < 0.001, (number, widths)
        spec = "@{}" + "".join(r">{\hsize=" + str(w) + r"\hsize\linewidth=\hsize}L" for w in widths) + "@{}"
        size = r"\scriptsize" if cols >= 5 else r"\footnotesize"
        result = [r"\begin{table}[H]", r"\centering", r"\caption{" + caption + "}" + self.add_label(kind, number),
                  size, r"\setlength{\tabcolsep}{3pt}", r"\renewcommand{\arraystretch}{1.2}",
                  r"\begin{tabularx}{\linewidth}{" + spec + "}", r"\toprule"]
        result.append(" & ".join(r"\textbf{" + c + "}" for c in rows[0]) + r" \\")
        result.append(r"\midrule")
        result.extend(" & ".join(row) + r" \\" for row in rows[1:])
        result.extend([r"\bottomrule", r"\end{tabularx}", r"\end{table}"])
        return "\n".join(result), end + 4

    def figure(self, number, caption):
        stem = {"1": "database-p95", "2": "database-tps", "3": "http-p95"}[number]
        path = f"transaction/images/4.3-0{number}-{stem}.pdf"
        assert (HERE.parent / path).is_file(), path
        self.counts["image_sources"] += 1
        short_caption = caption.split(";", 1)[0].replace("/", r"/\allowbreak{}")
        caption = caption.replace("/", r"/\allowbreak{}")
        return "\n".join([
            r"\begin{figure}[H]", r"\centering",
            r"\includegraphics[width=\linewidth]{" + path + "}",
            r"\caption[" + short_caption + "]{" + caption + "}" + self.add_label("Hình", number), r"\end{figure}",
        ])

    def convert(self, source):
        tokens = MD.parse(self.protect_math(source))
        result, i = [], 0
        while i < len(tokens):
            t = tokens[i]
            if t.type == "heading_open":
                content = tokens[i + 1].content
                match = re.fullmatch(r"3\.(\d+(?:\.\d+)*)\. (.+)", content)
                if not match:
                    raise ValueError(f"Unexpected heading: {content}")
                number, title = match.groups()
                command = "txsection" if t.tag == "h1" else "txsubsection"
                result.append("\\" + command + "{" + self.inline(MD.parseInline(title)[0].children) + "}" + self.add_label("Mục", number))
                i += 3
            elif t.type == "table_open":
                output, i = self.table(tokens, i)
                result.append(output)
            elif t.type == "fence":
                kind, number, caption = self.caption(tokens, i + 1, {"Mã nguồn", "Kết quả"})
                identifier = self.add_label(kind, number)
                if kind == "Mã nguồn":
                    language = {"sql": "OracleSQL", "redis": "Redis", "lua": "TransactionLua", "python": "Python", "javascript": "TransactionJavaScript"}[t.info]
                    # Listings reorders unescaped Vietnamese letters in XeLaTeX.
                    # Escaping each non-ASCII character preserves the code text.
                    assert "(*@" not in t.content and "@*)" not in t.content
                    code = "".join("(*@" + c + "@*)" if ord(c) > 127 else c for c in t.content)
                    assert code.replace("(*@", "").replace("@*)", "") == t.content
                    output = (r"\noindent\begin{minipage}{\linewidth}" + "\n" +
                              r"\begin{lstlisting}[language=" + language + ",caption={" + caption + "},label={" + label(kind, number) + "},basicstyle=\\ttfamily\\footnotesize,escapeinside={(*@}{@*)}]\n" +
                              code + r"\end{lstlisting}" + "\n" + r"\end{minipage}\par\medskip")
                else:
                    output = (r"\noindent\begin{minipage}{\linewidth}" + "\n" +
                              r"\captionof{txresult}{" + caption + "}" + identifier + "\n" +
                              r"\begin{Verbatim}[fontsize=\footnotesize,breakanywhere=true]" + "\n" + t.content +
                              r"\end{Verbatim}" + "\n" + r"\end{minipage}\par\medskip")
                result.append(output)
                i += 4
            elif t.type == "paragraph_open":
                inline = tokens[i + 1]
                placeholder = FIGURE_PLACEHOLDER.fullmatch(inline.content)
                if placeholder:
                    number, caption = placeholder.groups()
                    result.append(self.figure(number, self.inline(MD.parseInline(caption)[0].children)))
                    i += 3
                elif len(inline.children) == 1 and inline.children[0].type == "image":
                    kind, number, caption = self.caption(tokens, i + 3, {"Hình"})
                    result.append(self.figure(number, caption))
                    i += 6
                else:
                    if CAPTION.fullmatch(inline.content):
                        raise ValueError(f"Orphaned caption: {inline.content}")
                    result.append(self.inline(inline.children) + "\n")
                    i += 3
            elif t.type in ("bullet_list_open", "ordered_list_open"):
                env = "itemize" if t.type == "bullet_list_open" else "enumerate"
                result.append("\\begin{" + env + "}[leftmargin=*,itemsep=2pt,topsep=4pt]")
                i += 1
            elif t.type in ("bullet_list_close", "ordered_list_close"):
                result.append("\\end{" + ("itemize" if t.type == "bullet_list_close" else "enumerate") + "}")
                i += 1
            elif t.type == "list_item_open":
                result.append(r"\item")
                i += 1
            elif t.type == "list_item_close":
                i += 1
            else:
                raise ValueError(f"Unsupported block token: {t.type}")
        return "\n".join(result).rstrip() + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=HERE / "sections")
    parser.add_argument("--figures", action="store_true", help="Export the supplied SVG charts to vector PDF (requires PyGObject/Rsvg and Pycairo).")
    args = parser.parse_args()
    missing = [name for name, _ in PARTS if not (SOURCE / name).is_file()]
    if missing:
        parser.error(
            "Markdown nguồn chỉ lưu local trong " + str(SOURCE) + ". "
            "Thiếu: " + ", ".join(missing) + ". "
            "Build PDF bình thường bằng latexmk; không cần chạy script nhập Markdown."
        )
    args.output.mkdir(parents=True, exist_ok=True)
    converter = Converter()
    expected = Counter()
    outputs = {}
    for name, destination in PARTS:
        source = (SOURCE / name).read_text()
        for token in MD.parse(source):
            if token.type == "heading_open":
                expected["Mục"] += 1
            elif token.type == "inline":
                caption = CAPTION.fullmatch(token.content)
                if caption:
                    expected[caption[1]] += 1
                if FIGURE_PLACEHOLDER.fullmatch(token.content):
                    expected["Hình"] += 1
        outputs[destination] = (
            "% Generated from " + str((SOURCE / name).relative_to(ROOT)) + "; regenerate with report/transaction/import_markdown.py.\n" +
            "% Heading and cross-reference numbers are supplied by the parent LaTeX document.\n" + converter.convert(source))
    expected["image_sources"] = expected["Hình"]
    assert not converter.references - converter.labels, converter.references - converter.labels
    assert converter.counts == expected, (converter.counts, expected)
    # Validate all three chapters before replacing the current report content.
    for destination, output in outputs.items():
        (args.output / destination).write_text(output)
    if args.figures:
        export_figures()
    print(json.dumps(dict(converter.counts), ensure_ascii=False))


def export_figures():
    """Preserve chart geometry/data; remove the obsolete hand-written figure prefix.

    The SVG titles are glyph paths. Only their first eleven glyphs ("Hình 3.x · ")
    are removed; the chart, numbers, units, source notes and legend are untouched.
    Figure numbering is supplied once by the LaTeX caption instead.
    """
    import cairo
    import gi
    gi.require_version("Rsvg", "2.0")
    from gi.repository import Rsvg

    ns = {"svg": "http://www.w3.org/2000/svg"}
    output = HERE / "images"
    output.mkdir(exist_ok=True)
    for number, stem in enumerate(("database-p95", "database-tps", "http-p95"), 1):
        source = ROOT / f"report_assets/transaction/hinh-3-{number}-{stem}.svg"
        root = ET.fromstring(source.read_text(), parser=ET.XMLParser(target=ET.TreeBuilder(insert_comments=True)))
        changed = 0
        for group in root.findall(".//svg:g", ns):
            comments = [child for child in group if child.tag is ET.Comment]
            if not any(child.text.strip().startswith(f"Hình 3.{number} · ") for child in comments):
                continue
            glyphs = group.findall(".//svg:use", ns)
            parent = next(child for child in group if child.tag == "{" + ns["svg"] + "}g")
            offset = float(re.fullmatch(r"translate\(([\d.]+) 0\)", glyphs[11].attrib["transform"])[1])
            for glyph in glyphs[:11]:
                parent.remove(glyph)
            for glyph in glyphs[11:]:
                x = float(re.fullmatch(r"translate\(([\d.]+) 0\)", glyph.attrib["transform"])[1])
                glyph.set("transform", f"translate({x - offset} 0)")
            for comment in comments:
                comment.text = re.sub(r"Hình 3\.\d · ", "", comment.text)
            changed += 1
        assert changed == 1, source
        for element in root.iter():
            if isinstance(element.tag, str) and element.tag.endswith("}title") and element.text:
                element.text = re.sub(r"Hình 3\.\d · ", "", element.text)
        _, _, width, height = map(float, root.attrib["viewBox"].split())
        surface = cairo.PDFSurface(str(output / f"4.3-0{number}-{stem}.pdf"), width, height)
        context = cairo.Context(surface)
        handle = Rsvg.Handle.new_from_data(ET.tostring(root))
        viewport = Rsvg.Rectangle()
        viewport.x, viewport.y, viewport.width, viewport.height = 0, 0, width, height
        handle.render_document(context, viewport)
        surface.finish()


if __name__ == "__main__":
    main()
