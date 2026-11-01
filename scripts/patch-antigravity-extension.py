#!/usr/bin/env python3
"""
Patches the Google Antigravity extension (google.google-antigravity) so that:
1. It is copied from ~/.vscode/extensions to ~/.vscode-server/extensions.
2. It writes the ephemeral port of `agy --hub` to /tmp/agy_hub_port.txt.
3. It uses http://localhost:45414 (agy-web-proxy) as the effectiveUrl to bypass
   CSP frame-ancestors restrictions when running inside VS Code Web / Android APK.
"""
import glob
import os
import shutil

HOME = os.path.expanduser("~")
SRC_DIRS = sorted(glob.glob(os.path.join(HOME, ".vscode/extensions/google.google-antigravity-*")))
SERVER_EXT_DIR = os.path.join(HOME, ".vscode-server/extensions")

if not SRC_DIRS:
    print("No Google Antigravity installation found in ~/.vscode/extensions/")
    exit(0)

os.makedirs(SERVER_EXT_DIR, exist_ok=True)
for src in SRC_DIRS:
    dst = os.path.join(SERVER_EXT_DIR, os.path.basename(src))
    if not os.path.exists(dst):
        shutil.copytree(src, dst)
        print(f"Copied extension to {dst}")

TARGETS = sorted(
    glob.glob(os.path.join(HOME, ".vscode/extensions/google.google-antigravity-*/extension.js"))
    + glob.glob(os.path.join(HOME, ".vscode-server/extensions/google.google-antigravity-*/extension.js"))
)

OLD_SNIPPET = 'const url = await hubManager.start();const effectiveUrl = await vscode.env.asExternalUri(vscode.Uri.parse(url));'
NEW_SNIPPET = 'const url = await hubManager.start();try{require("fs").writeFileSync("/tmp/agy_hub_port.txt", String(new URL(url).port));}catch(e){}const effectiveUrl = "http://localhost:45414";'

for path in TARGETS:
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    if OLD_SNIPPET in content:
        content = content.replace(OLD_SNIPPET, NEW_SNIPPET)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Patched: {path}")
    elif "agy_hub_port.txt" in content:
        print(f"Already patched: {path}")
    else:
        print(f"Target snippet not found in: {path}")
