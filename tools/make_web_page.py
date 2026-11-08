#!/usr/bin/env python3
"""Writes the browser build's pages from web/index.html and the TeaVM output.

  release/Hexfront.html          the game as one self-contained file, without online play (no network code at all)
  release/Hexfront-online.html   the same with online play, also one file
  release/web/index.html + hexfront.js   the online version as a folder to host on any web server

Usage: make_web_page.py <hexfront.js> <hexfront-online.js> <build stamp>
"""
import os
import sys

js_path, online_path, stamp = sys.argv[1], sys.argv[2], sys.argv[3]
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
template = open(os.path.join(root, "web", "index.html"), encoding="utf-8").read().replace("%BUILD%", stamp)


def inline(js):
    # a "</script" inside the code would end the tag early
    return "<script>\n" + js.replace("</script", "<\\/script") + "\n</script>"


def write(rel, text):
    with open(os.path.join(root, rel), "w", encoding="utf-8") as f:
        f.write(text)


offline = open(js_path, encoding="utf-8").read()
online = open(online_path, encoding="utf-8").read()
os.makedirs(os.path.join(root, "release", "web"), exist_ok=True)
write(os.path.join("release", "web", "hexfront.js"), online)
write(os.path.join("release", "web", "index.html"), template.replace("%SCRIPT%", '<script src="hexfront.js"></script>'))
write(os.path.join("release", "Hexfront.html"), template.replace("%SCRIPT%", inline(offline)))
write(os.path.join("release", "Hexfront-online.html"), template.replace("%SCRIPT%", inline(online)))
size = lambda rel: os.path.getsize(os.path.join(root, "release", rel)) // 1024
print("wrote release/Hexfront.html (%d KB), release/Hexfront-online.html (%d KB) and release/web/ (online)"
      % (size("Hexfront.html"), size("Hexfront-online.html")))
