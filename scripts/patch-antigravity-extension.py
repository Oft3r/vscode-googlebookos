#!/usr/bin/env python3
"""
Patches every installed Google Antigravity extension (google.google-antigravity-*)
so that it works inside VS Code Web (`code serve-web`) and the Android APK:

1. It writes the ephemeral port of `agy --hub` to /tmp/agy_hub_port.txt.
2. It uses http://localhost:45414 (agy-web-proxy) as the webview `effectiveUrl`,
   bypassing the CSP `frame-ancestors` restriction of the hub.

The script is idempotent and supports both the readable (<= 1.10.x) and the
minified (>= 1.11.x) builds of the extension. It is meant to be run
automatically by `agy-extension-patch.path` whenever the extension updates.
"""
import glob
import os
import re

HOME = os.path.expanduser("~")
MARKER = "agy_hub_port.txt"
PROXY_URL = "http://localhost:45414"

TARGETS = sorted(
    glob.glob(os.path.join(HOME, ".vscode/extensions/google.google-antigravity-*/extension.js"))
    + glob.glob(os.path.join(HOME, ".vscode-server/extensions/google.google-antigravity-*/extension.js"))
)

# Readable build (<= 1.10.x)
OLD_SNIPPET = 'const url = await hubManager.start();const effectiveUrl = await vscode.env.asExternalUri(vscode.Uri.parse(url));'
NEW_SNIPPET = ('const url = await hubManager.start();try{require("fs").writeFileSync("/tmp/' + MARKER
               + '", String(new URL(url).port));}catch(e){}const effectiveUrl = "' + PROXY_URL + '";')

# Minified build (>= 1.11.x), e.g.:
#   effectiveUrl:c(await d(n.env.asExternalUri(n.Uri.parse(a)))).toString()
MINIFIED_RE = re.compile(
    r'effectiveUrl:([\w$]+)\(await ([\w$]+)\(([\w$]+)\.env\.asExternalUri\(\3\.Uri\.parse\(([\w$]+)\)\)\)\)'
)


def minified_repl(m):
    wrap, awaiter, vsc, url = m.groups()
    uri = (f'(()=>{{try{{require("fs").writeFileSync("/tmp/{MARKER}",String(new URL({url}).port))}}catch(e){{}}'
           f'return {vsc}.Uri.parse("{PROXY_URL}")}})()')
    return f'effectiveUrl:{wrap}(await {awaiter}(Promise.resolve({uri})))'


for path in TARGETS:
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    if MARKER in content:
        print(f"Already patched: {path}")
        continue
    if OLD_SNIPPET in content:
        content = content.replace(OLD_SNIPPET, NEW_SNIPPET)
    else:
        content, count = MINIFIED_RE.subn(minified_repl, content)
        if count == 0:
            print(f"WARNING: target code not found (unsupported version?): {path}")
            continue
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Patched: {path}")
