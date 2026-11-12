"""把项目中的 Markdown 需求文档转换为 Word（.docx）。

用法：python tools/md2docx.py <input.md> <output.docx> [--toc]
支持：标题、表格、代码块（流程图）、引用、列表、**加粗**、`代码`、【待确认】高亮。
第一个一级标题 + 紧随其后的表格生成封面。
"""
import re
import sys

from docx import Document
from docx.enum.section import WD_ORIENT  # noqa: F401
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

BODY_FONT = "宋体"
HEAD_FONT = "微软雅黑"
MONO_FONT = "新宋体"
BLUE = RGBColor(0x1C, 0x5C, 0xAB)
GREY = RGBColor(0x59, 0x59, 0x59)
HILITE = RGBColor(0xC0, 0x39, 0x2B)
BOX_ASCII = str.maketrans({"─": "-", "│": "|", "►": ">", "▼": "v", "┌": "+", "┐": "+", "└": "+", "┘": "+", "├": "+", "┤": "+", "┬": "+", "┴": "+", "┼": "+"})
WIDE_COLS = ("客户答复", "客户填写", "是否提供", "签字", "意见")


# ---------------------------------------------------------------- helpers
def set_font(run, name=BODY_FONT, size=None, bold=None, color=None):
    run.font.name = name
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    for k in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        rfonts.set(qn(k), name)
    if size:
        run.font.size = Pt(size)
    if bold is not None:
        run.bold = bold
    if color is not None:
        run.font.color.rgb = color


def style_font(style, name, size, bold=False, color=None):
    style.font.name = name
    style.font.size = Pt(size)
    style.font.bold = bold
    if color is not None:
        style.font.color.rgb = color
    rpr = style.element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    for k in ("w:ascii", "w:hAnsi", "w:eastAsia", "w:cs"):
        rfonts.set(qn(k), name)
    for k in ("w:asciiTheme", "w:hAnsiTheme", "w:eastAsiaTheme", "w:cstheme"):
        if rfonts.get(qn(k)) is not None:
            del rfonts.attrib[qn(k)]


def shade(el, fill):
    """el: cell (_tc) or paragraph pPr."""
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    el.append(shd)


def cell_shade(cell, fill):
    shade(cell._tc.get_or_add_tcPr(), fill)


def table_borders(table, color="A6B4C8"):
    tblpr = table._tbl.tblPr
    borders = OxmlElement("w:tblBorders")
    for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
        e = OxmlElement(f"w:{edge}")
        e.set(qn("w:val"), "single")
        e.set(qn("w:sz"), "4")
        e.set(qn("w:space"), "0")
        e.set(qn("w:color"), color)
        borders.append(e)
    tblpr.append(borders)


def repeat_header(row):
    trpr = row._tr.get_or_add_trPr()
    h = OxmlElement("w:tblHeader")
    h.set(qn("w:val"), "true")
    trpr.append(h)


def add_field(paragraph, instr):
    run = paragraph.add_run()
    for tag, extra in (("begin", None), ("instr", instr), ("separate", None), ("end", None)):
        if tag == "instr":
            it = OxmlElement("w:instrText")
            it.set(qn("xml:space"), "preserve")
            it.text = extra
            run._r.append(it)
        else:
            fc = OxmlElement("w:fldChar")
            fc.set(qn("w:fldCharType"), tag)
            run._r.append(fc)
            if tag == "separate":
                t = OxmlElement("w:t")
                t.text = "1" if "PAGE" in instr else "（右键选择“更新域”生成目录）"
                run._r.append(t)
    return run


INLINE = re.compile(r"(\*\*.+?\*\*|`.+?`|【待确认[^】]*】|【建议】)")


def add_inline(paragraph, text, size=None, bold=False, color=None, font=BODY_FONT):
    for part in INLINE.split(text):
        if not part:
            continue
        if part.startswith("**") and part.endswith("**"):
            r = paragraph.add_run(part[2:-2])
            set_font(r, font, size, True, color)
        elif part.startswith("`") and part.endswith("`"):
            r = paragraph.add_run(part[1:-1])
            set_font(r, "Consolas", size, bold, RGBColor(0x1C, 0x5C, 0xAB))
        elif part.startswith("【"):
            r = paragraph.add_run(part)
            set_font(r, font, size, True, HILITE)
        else:
            r = paragraph.add_run(part)
            set_font(r, font, size, bold, color)


def para_spacing(p, before=0, after=4, line=1.35):
    pf = p.paragraph_format
    pf.space_before = Pt(before)
    pf.space_after = Pt(after)
    pf.line_spacing = line


# ---------------------------------------------------------------- blocks
def parse_row(line):
    line = line.strip()
    if line.startswith("|"):
        line = line[1:]
    if line.endswith("|"):
        line = line[:-1]
    return [c.strip() for c in line.split("|")]


def text_cm(t, size_pt=9):
    """估算文字宽度（cm）：中文 1 em，ASCII 0.55 em。"""
    em = sum(1.0 if ord(ch) > 0x2E80 else 0.55 for ch in t)
    return em * size_pt * 0.0353 + 0.45


def col_widths(rows, total_cm):
    n = len(rows[0])
    weights, mins = [], []
    for i in range(n):
        cells = [r[i] if i < len(r) else "" for r in rows]
        lens = [len(c) for c in cells]
        w = max(4, min(60, max(lens) * 0.6 + sum(lens) / len(lens) * 0.4))
        if any(k in rows[0][i] for k in WIDE_COLS):
            w = max(w, 22)
        weights.append(w)
        # 短列（编号、优先级等）保证不换行
        longest = max(cells[1:] or [""], key=len)
        m = text_cm(rows[0][i])
        if len(longest) <= 6:
            m = max(m, text_cm(longest))
        mins.append(m)
    widths = [total_cm * w / sum(weights) for w in weights]
    for _ in range(3):
        fixed = [i for i in range(n) if widths[i] < mins[i]]
        if not fixed:
            break
        rest = total_cm - sum(mins[i] for i in fixed)
        free = [i for i in range(n) if i not in fixed]
        fw = sum(weights[i] for i in free)
        widths = [mins[i] if i in fixed else rest * weights[i] / fw for i in range(n)]
    return widths


def add_table(doc, rows, total_cm, kv=False):
    n = max(len(r) for r in rows)
    rows = [r + [""] * (n - len(r)) for r in rows]
    t = doc.add_table(rows=len(rows), cols=n)
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    table_borders(t)
    widths = col_widths(rows, total_cm)
    if kv and n == 2:
        widths = [total_cm * 0.28, total_cm * 0.72]
    for ri, r in enumerate(rows):
        for ci, val in enumerate(r):
            c = t.cell(ri, ci)
            c.width = Cm(widths[ci])
            p = c.paragraphs[0]
            para_spacing(p, 1, 1, 1.2)
            header = ri == 0 and not kv
            add_inline(p, val, size=9 if not kv else 10.5, bold=header or (kv and ci == 0),
                       color=None)
            if header:
                cell_shade(c, "DCE6F2")
            elif kv and ci == 0:
                cell_shade(c, "F2F5FA")
            elif any(k in rows[0][ci] for k in WIDE_COLS) and not kv:
                cell_shade(c, "FFFDF0")
    if not kv:
        repeat_header(t.rows[0])
    doc.add_paragraph()
    return t


def add_code(doc, lines, total_cm):
    t = doc.add_table(rows=1, cols=1)
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    table_borders(t, "D0D7E2")
    c = t.cell(0, 0)
    c.width = Cm(total_cm)
    cell_shade(c, "F6F8FB")
    first = True
    for ln in lines:
        p = c.paragraphs[0] if first else c.add_paragraph()
        first = False
        para_spacing(p, 0, 0, 1.0)
        # 制表符在中文等宽字体里是全角宽度，换成 ASCII 保证流程图对齐
        ln = ln.translate(BOX_ASCII)
        r = p.add_run(ln if ln else " ")
        set_font(r, MONO_FONT, 9)
    doc.add_paragraph()


def add_quote(doc, text):
    p = doc.add_paragraph()
    para_spacing(p, 2, 2, 1.45)
    p.paragraph_format.left_indent = Cm(0.4)
    ppr = p._p.get_or_add_pPr()
    bdr = OxmlElement("w:pBdr")
    left = OxmlElement("w:left")
    left.set(qn("w:val"), "single")
    left.set(qn("w:sz"), "18")
    left.set(qn("w:space"), "8")
    left.set(qn("w:color"), "2A78D6")
    bdr.append(left)
    ppr.append(bdr)
    shade(ppr, "F2F6FC")
    add_inline(p, text, size=10, color=GREY)


# ---------------------------------------------------------------- main
def convert(src, dst, toc=False):
    lines = open(src, encoding="utf-8").read().splitlines()
    doc = Document()

    sec = doc.sections[0]
    sec.page_height, sec.page_width = Cm(29.7), Cm(21.0)
    sec.left_margin = sec.right_margin = Cm(2.2)
    sec.top_margin, sec.bottom_margin = Cm(2.5), Cm(2.2)
    text_w = 21.0 - 4.4

    st = doc.styles
    style_font(st["Normal"], BODY_FONT, 10.5)
    style_font(st["Title"], HEAD_FONT, 24, True, RGBColor(0x0F, 0x2A, 0x4A))
    style_font(st["Heading 1"], HEAD_FONT, 16, True, RGBColor(0x0F, 0x2A, 0x4A))
    style_font(st["Heading 2"], HEAD_FONT, 13, True, BLUE)
    style_font(st["Heading 3"], HEAD_FONT, 11.5, True, RGBColor(0x1B, 0x24, 0x30))
    for name, b, a in (("Heading 1", 18, 8), ("Heading 2", 12, 6), ("Heading 3", 8, 4)):
        st[name].paragraph_format.space_before = Pt(b)
        st[name].paragraph_format.space_after = Pt(a)
        st[name].paragraph_format.keep_with_next = True

    title = None
    i = 0
    # ---- 封面：第一个 # 标题 + 紧随其后的表格
    while i < len(lines) and not lines[i].startswith("# "):
        i += 1
    if i < len(lines):
        title = lines[i][2:].strip()
        i += 1
        for _ in range(6):
            doc.add_paragraph()
        m = re.match(r"(.*?软件)\s*(.*)", title)
        main, sub = (m.group(1), m.group(2)) if m else (title, "")
        p = doc.add_paragraph(style="Title")
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(main)
        set_font(r, HEAD_FONT, 26, True, RGBColor(0x0F, 0x2A, 0x4A))
        if sub:
            p = doc.add_paragraph()
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            r = p.add_run(sub)
            set_font(r, HEAD_FONT, 20, True, BLUE)
        for _ in range(5):
            doc.add_paragraph()
        while i < len(lines) and not lines[i].strip():
            i += 1
        rows = []
        while i < len(lines) and lines[i].strip().startswith("|"):
            row = parse_row(lines[i])
            if not all(re.fullmatch(r":?-{2,}:?", c) for c in row):
                rows.append(row)
            i += 1
        if rows:
            add_table(doc, rows[1:] if rows[0][0] in ("项目",) else rows, 12, kv=True)
        doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)

    if toc:
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run("目　录")
        set_font(r, HEAD_FONT, 16, True)
        add_field(doc.add_paragraph(), 'TOC \\o "1-3" \\h \\z \\u')
        doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)

    # ---- 正文
    while i < len(lines):
        ln = lines[i]
        s = ln.strip()
        if s.startswith("```"):
            buf = []
            i += 1
            while i < len(lines) and not lines[i].strip().startswith("```"):
                buf.append(lines[i])
                i += 1
            add_code(doc, buf, text_w)
            i += 1
            continue
        if s.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                row = parse_row(lines[i])
                if not all(re.fullmatch(r":?-{2,}:?", c) for c in row):
                    rows.append(row)
                i += 1
            add_table(doc, rows, text_w)
            continue
        if s.startswith(">"):
            buf = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                buf.append(lines[i].strip()[1:].strip())
                i += 1
            for para in "\n".join(buf).split("\n\n"):
                if para.strip():
                    add_quote(doc, para.replace("\n", ""))
            continue
        if s in ("---", "***") or not s:
            i += 1
            continue
        m = re.match(r"(#{2,4})\s+(.*)", s)
        if m:
            level = len(m.group(1)) - 1
            h = doc.add_heading(level=level)
            add_inline(h, m.group(2), font=HEAD_FONT)
            i += 1
            continue
        m = re.match(r"(\s*)[-*]\s+(.*)", ln)
        if m:
            p = doc.add_paragraph(style="List Bullet")
            para_spacing(p, 0, 2)
            p.paragraph_format.left_indent = Cm(0.75 + len(m.group(1)) * 0.35)
            add_inline(p, m.group(2))
            i += 1
            continue
        m = re.match(r"(\s*)(\d+)\.\s+(.*)", ln)
        if m:
            p = doc.add_paragraph()
            para_spacing(p, 0, 2)
            p.paragraph_format.left_indent = Cm(0.75 + len(m.group(1)) * 0.35)
            p.paragraph_format.first_line_indent = Cm(-0.5)
            add_inline(p, f"{m.group(2)}. {m.group(3)}")
            i += 1
            continue
        p = doc.add_paragraph()
        para_spacing(p, 0, 4, 1.5)
        add_inline(p, s)
        i += 1

    # ---- 页眉页脚
    hp = sec.header.paragraphs[0]
    hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    r = hp.add_run(title or "")
    set_font(r, BODY_FONT, 9, color=GREY)
    sec.different_first_page_header_footer = True
    fp = sec.footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    for part in ("第 ", "PAGE", " 页 / 共 ", "NUMPAGES", " 页"):
        if part in ("PAGE", "NUMPAGES"):
            set_font(add_field(fp, part), BODY_FONT, 9, color=GREY)
        else:
            set_font(fp.add_run(part), BODY_FONT, 9, color=GREY)

    doc.core_properties.title = title or ""
    doc.core_properties.author = "宏禹水利管理软件项目组"
    doc.save(dst)
    print("saved", dst)


if __name__ == "__main__":
    convert(sys.argv[1], sys.argv[2], "--toc" in sys.argv)
