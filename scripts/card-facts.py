#!/usr/bin/env python3
"""Copies the phone app's card facts into the watch's string resources.

    scripts/card-facts.py ../cards

Writes values/card_facts.xml from the phone's src/data/card-facts.ts and
values-<lang>/card_facts.xml from its src/locales/<lang>/content/facts.json,
the same facts in the same order. Translations are made on the phone
(npm run i18n); a stale one (its English changed) stops the copy.
"""
import ast
import json
import re
import sys
from pathlib import Path

# The phone's language codes, and the watch's resource folders for them.
FOLDERS = {"fr": "fr", "es": "es", "it": "it", "pt": "pt", "tr": "tr", "id": "in"}

phone = Path(sys.argv[1] if len(sys.argv) > 1 else "../cards")
res = Path(__file__).resolve().parent.parent / "app/src/main/res"


def source_hash(value):
    """FNV-1a of the value as JSON, as the phone's sourceHash() makes it."""
    text = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    h = 0x811C9DC5
    for unit in text.encode("utf-16-le").hex(" ", 2).split():
        h ^= int(unit[2:] + unit[:2], 16)
        h = (h * 0x01000193) & 0xFFFFFFFF
    return f"{h:08x}"


def english():
    source = (phone / "src/data/card-facts.ts").read_text()
    body = source[source.index("CARD_FACTS"):]
    body = body[body.index("= [") + 3 : body.index("\n];")]
    # One string literal a line, in either quotes (Python reads both alike).
    strings = re.findall(r'^\s*("(?:[^"\\]|\\.)*"|\'(?:[^\'\\]|\\.)*\'),?\s*$', body, re.M)
    lines = [line for line in body.splitlines() if line.strip() and not line.strip().startswith("//")]
    if len(strings) != len(lines):
        sys.exit("CARD_FACTS isn't one plain string a line: update scripts/card-facts.py.")
    return [ast.literal_eval(m) for m in strings]


def escape(text):
    text = text.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace('"', '\\"').replace("'", "\\'")
    if text[:1] in "@?":
        text = "\\" + text
    return f'"{text}"' if "  " in text or text != text.strip() else text


def write(folder, facts, comment):
    lines = ['<?xml version="1.0" encoding="utf-8"?>', comment, "<resources>", '    <string-array name="card_facts">']
    lines += [f"        <item>{escape(fact)}</item>" for fact in facts]
    lines += ["    </string-array>", "</resources>", ""]
    (res / folder).mkdir(exist_ok=True)
    (res / folder / "card_facts.xml").write_text("\n".join(lines))


facts = english()
write("values", facts, "<!-- Playing card trivia, one of which sits at the foot of the home list: the phone app's (its src/data/card-facts.ts), copied by scripts/card-facts.py. -->")
for language, folder in FOLDERS.items():
    content = json.loads((phone / f"src/locales/{language}/content/facts.json").read_text())
    translated = []
    for number, fact in enumerate(facts, 1):
        entry = content.get(str(number))
        if entry is None or entry["hash"] != source_hash(fact):
            sys.exit(f"{language} fact {number} is missing or stale on the phone: translate it there first.")
        translated.append(entry["value"])
    write(f"values-{folder}", translated, f"<!-- The phone app's card facts in this language (its src/locales/{language}/content/facts.json), copied by scripts/card-facts.py. -->")
print(f"Copied {len(facts)} facts in {len(FOLDERS) + 1} languages.")
