#!/usr/bin/env python3
"""Writes the browser build's pages from web/index.html and the TeaVM output.

  release/web/index.html + hexfront.js   a folder to host on any web server
  release/Hexfront.html                  the same game as one self-contained file: double-click it to play

Usage: make_web_page.py <hexfront.js> <build stamp>
"""
import os
import sys

js_path, stamp = sys.argv[1], sys.argv[2]
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
template = open(os.path.join(root, "web", "index.html"), encoding="utf-8").read().replace("%BUILD%", stamp)
js = open(js_path, encoding="utf-8").read()

os.makedirs(os.path.join(root, "release", "web"), exist_ok=True)
with open(os.path.join(root, "release", "web", "hexfront.js"), "w", encoding="utf-8") as f:
    f.write(js)
with open(os.path.join(root, "release", "web", "index.html"), "w", encoding="utf-8") as f:
    f.write(template.replace("%SCRIPT%", '<script src="hexfront.js"></script>'))
# inline copy: a "</script" inside the code would end the tag early
inline = "<script>\n" + js.replace("</script", "<\\/script") + "\n</script>"
with open(os.path.join(root, "release", "Hexfront.html"), "w", encoding="utf-8") as f:
    f.write(template.replace("%SCRIPT%", inline))
print("wrote release/web/index.html, release/web/hexfront.js and release/Hexfront.html (%d KB)"
      % (os.path.getsize(os.path.join(root, "release", "Hexfront.html")) // 1024))
