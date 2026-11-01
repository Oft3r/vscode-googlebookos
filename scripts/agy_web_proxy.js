const http = require('http');
const net = require('net');
const fs = require('fs');

const LISTEN_PORT = 45414;
const PORT_FILE = '/tmp/agy_hub_port.txt';

function getTargetPort() {
  try {
    return parseInt(fs.readFileSync(PORT_FILE, 'utf8').trim(), 10);
  } catch (e) {
    return 0;
  }
}

const server = http.createServer((req, res) => {
  const targetPort = getTargetPort();
  if (!targetPort) {
    res.writeHead(503);
    res.end('Antigravity Hub not started yet');
    return;
  }

  const options = {
    hostname: '127.0.0.1',
    port: targetPort,
    path: req.url,
    method: req.method,
    headers: { ...req.headers, host: `127.0.0.1:${targetPort}` }
  };
  if (options.headers.origin) {
    options.headers.origin = `http://127.0.0.1:${targetPort}`;
  }

  const proxyReq = http.request(options, (proxyRes) => {
    const headers = { ...proxyRes.headers };
    delete headers['content-security-policy'];
    delete headers['x-frame-options'];
    delete headers['cross-origin-opener-policy'];
    headers['access-control-allow-origin'] = req.headers.origin || '*';
    headers['access-control-allow-credentials'] = 'true';
    res.writeHead(proxyRes.statusCode || 200, headers);
    proxyRes.pipe(res);
  });

  proxyReq.on('error', (err) => {
    res.writeHead(502);
    res.end(err.message);
  });

  req.pipe(proxyReq);
});

server.on('upgrade', (req, socket, head) => {
  const targetPort = getTargetPort();
  if (!targetPort) {
    socket.destroy();
    return;
  }
  const upstream = net.connect(targetPort, '127.0.0.1', () => {
    let rawHeaders = `${req.method} ${req.url} HTTP/${req.httpVersion}\r\n`;
    for (let i = 0; i < req.rawHeaders.length; i += 2) {
      const k = req.rawHeaders[i];
      let v = req.rawHeaders[i + 1];
      if (k.toLowerCase() === 'host') v = `127.0.0.1:${targetPort}`;
      if (k.toLowerCase() === 'origin') v = `http://127.0.0.1:${targetPort}`;
      rawHeaders += `${k}: ${v}\r\n`;
    }
    rawHeaders += '\r\n';
    upstream.write(rawHeaders);
    if (head && head.length) upstream.write(head);
    socket.pipe(upstream);
    upstream.pipe(socket);
  });
  upstream.on('error', () => socket.destroy());
  socket.on('error', () => upstream.destroy());
});

server.listen(LISTEN_PORT, '0.0.0.0', () => {
  console.log(`Antigravity proxy listening on 0.0.0.0:${LISTEN_PORT}`);
});
