#!/usr/bin/env bash
set -euo pipefail

echo "=== [1/7] Installing dependencies and Microsoft GPG key ==="
sudo apt-get update
sudo apt-get install -y gpg wget curl apt-transport-https unattended-upgrades nodejs python3

wget -qO- https://packages.microsoft.com/keys/microsoft.asc | gpg --dearmor > /tmp/microsoft.gpg
sudo install -D -o root -g root -m 644 /tmp/microsoft.gpg /usr/share/keyrings/microsoft.gpg
rm -f /tmp/microsoft.gpg

echo "=== [2/7] Installing Visual Studio Code (ARM64) ==="
if ! command -v code >/dev/null 2>&1; then
    curl -L "https://code.visualstudio.com/sha/download?build=stable&os=linux-deb-arm64" -o /tmp/vscode-arm64.deb
    sudo apt-get install -y /tmp/vscode-arm64.deb
    rm -f /tmp/vscode-arm64.deb
fi

echo "=== [3/7] Applying Wayland + GPU flags for Terminal execution ==="
sudo ln -sf /usr/share/code/bin/code /usr/bin/code
if ! grep -q -- '--ozone-platform=wayland' /usr/share/code/bin/code; then
    sudo sed -i 's/"$ELECTRON" "$CLI" "$@"/"$ELECTRON" "$CLI" --ozone-platform=wayland --disable-gpu "$@"/g' /usr/share/code/bin/code
fi

mkdir -p "$HOME/.config" "$HOME/.vscode"
cat << 'EOF' > "$HOME/.config/code-flags.conf"
--ozone-platform=wayland
--disable-gpu
--password-store=basic
EOF

echo "=== [4/7] Configuring persistent GitHub/Microsoft authentication ==="
cat << 'EOF' > "$HOME/.vscode/argv.json"
{
	"enable-crash-reporter": true,
	"password-store": "basic"
}
EOF

echo "=== [5/7] Configuring automatic APT updates and post-upgrade hook ==="
sudo tee /etc/apt/apt.conf.d/51unattended-upgrades-vscode >/dev/null << 'EOF'
Unattended-Upgrade::Origins-Pattern {
    "origin=code stable,label=code stable";
};
EOF

sudo tee /etc/apt/apt.conf.d/99vscode-wayland-fix >/dev/null << 'EOF'
DPkg::Post-Invoke {
    "if [ -f /usr/share/code/bin/code ] && ! grep -q -- '--ozone-platform=wayland' /usr/share/code/bin/code; then sed -i 's/\"$ELECTRON\" \"$CLI\" \"$@\"/\"$ELECTRON\" \"$CLI\" --ozone-platform=wayland --disable-gpu \"$@\"/g' /usr/share/code/bin/code; systemctl try-restart vscode-web.service >/dev/null 2>&1 || true; fi";
};
EOF

echo "=== [6/7] Installing systemd service for VS Code Web (:18080) ==="
sudo tee /etc/systemd/system/vscode-web.service >/dev/null << EOF
[Unit]
Description=Visual Studio Code Web Server for Android App
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$HOME
Environment="VSCODE_AGENT_FOLDER=$HOME/.vscode-server"
ExecStart=/usr/share/code/bin/code-tunnel serve-web --host 0.0.0.0 --port 18080 --without-connection-token --accept-server-license-terms
Restart=always
RestartSec=2

[Install]
WantedBy=multi-user.target
EOF

echo "=== [7/7] Configuring proxy and patch for Google Antigravity ==="
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sudo cp "$SCRIPT_DIR/agy_web_proxy.js" /usr/local/bin/agy_web_proxy.js
sudo chmod +x /usr/local/bin/agy_web_proxy.js

sudo tee /etc/systemd/system/agy-web-proxy.service >/dev/null << EOF
[Unit]
Description=Antigravity WebView CSP & WebSocket Proxy
After=network.target

[Service]
Type=simple
User=$USER
ExecStart=/usr/bin/node /usr/local/bin/agy_web_proxy.js
Restart=always
RestartSec=2

[Install]
WantedBy=multi-user.target
EOF

python3 "$SCRIPT_DIR/patch-antigravity-extension.py" || true

sudo systemctl daemon-reload
sudo systemctl enable --now vscode-web.service
sudo systemctl enable --now agy-web-proxy.service

echo "=== Setup completed successfully! ==="
