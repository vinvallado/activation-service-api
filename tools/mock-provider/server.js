const http = require('node:http');

const calls = new Map();
let requestCount = 0;

function json(response, status, body, headers = {}) {
  response.writeHead(status, { 'content-type': 'application/json', ...headers });
  response.end(JSON.stringify(body));
}

http.createServer((request, response) => {
  const url = new URL(request.url, 'http://localhost');
  if (request.method === 'GET' && url.pathname === '/health') return json(response, 200, { status: 'UP' });
  if (request.method === 'GET' && url.pathname === '/stats') return json(response, 200, { requests: requestCount, idempotencyKeys: Object.fromEntries(calls) });
  if (request.method !== 'POST' || url.pathname !== '/provider/v1/activate') return json(response, 404, { error: 'not_found' });

  requestCount += 1;
  const key = request.headers['idempotency-key'];
  if (!key) return json(response, 400, { error: 'missing_idempotency_key' });
  calls.set(key, (calls.get(key) || 0) + 1);
  const scenario = request.headers['x-provider-scenario'] || 'success';
  if (scenario === 'bad-request') return json(response, 400, { error: 'invalid_product' });
  if (scenario === 'server-error' || scenario === 'always-500') return json(response, 500, { error: 'temporary_failure' });
  if (scenario === 'rate-limited') return json(response, 429, { error: 'too_many_requests' }, { 'retry-after': '3' });
  if (scenario === 'unavailable') return json(response, 503, { error: 'unavailable' }, { 'retry-after': new Date(Date.now() + 3000).toUTCString() });
  if (scenario === 'timeout') return setTimeout(() => json(response, 200, { activated: true }), 10000);
  return json(response, 200, { activated: true, idempotencyKey: key });
}).listen(9099, '0.0.0.0');
