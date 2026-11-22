# -*- coding: utf-8 -*-
"""把用户整理的 答案.txt（按章节顺序）规范化为 assets/answers_nation.txt。

输入行形如：
    1.1导论              <- 章节标题（数字.数字 开头）
    1.钢少气多            <- 题号.答案（填空）
    2.A.邓小平            <- 题号.选项字母.选项文本（单选/判断）
    3.ABCDE              <- 题号.多选字母

输出每行：chapter<TAB>index<TAB>answer
其中 answer 为字母串（单选/多选/判断）或填空文本。
"""
import re
import sys

src = r"D:\DSHWorkplace\cxbuild\tools\answers_src.txt"
dst = r"D:\DSHWorkplace\cxbuild\project\app\src\main\assets\answers_nation.txt"

chapter_re = re.compile(r"^\s*(\d+)[.．](\d+)(?:[.．](\d+))?\s*(.+?)\s*$")
answer_re = re.compile(r"^\s*(\d+)\s*[.．、]\s*(.+?)\s*$")
opt_re = re.compile(r"^([A-Fa-f]{1,6})\s*[.．、]\s*(.*)$")
letters_re = re.compile(r"^[A-Fa-f]{1,8}$")

rows = []
chapter = None
idx = 0
seen_header = {}

with open(src, "r", encoding="utf-8-sig") as f:
    for raw in f:
        line = raw.strip()
        if not line:
            continue
        mch = chapter_re.match(line)
        if mch:
            num = "%s.%s" % (mch.group(1), mch.group(2))
            if mch.group(3):
                num += "." + mch.group(3)
            chapter = num + mch.group(4).replace(" ", "")
            idx = 0
            continue
        m = answer_re.match(line)
        if not m or chapter is None:
            continue
        n = int(m.group(1))
        rest = m.group(2).strip()
        # 完整保留原文：判断题的「A.对」必须同时保留字母与含义，
        # 单选题的「A.邓小平」也要保留选项文本，填空则原样保留。
        if letters_re.match(rest):
            ans = rest.upper()
        else:
            ans = rest
        rows.append((chapter, n, ans))

with open(dst, "w", encoding="utf-8", newline="\n") as f:
    for ch, n, ans in rows:
        f.write("%s\t%d\t%s\n" % (ch, n, ans))

print("chapters:", len({r[0] for r in rows}))
print("answers:", len(rows))
print("sample:", rows[:5])
print("tail:", rows[-3:])
