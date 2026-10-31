const http = require('node:http');
const crypto = require('node:crypto');

const calls = new Map();
const scenarioCalls = new Map();
let requestCount = 0;
let activeScenario = 'success';
const productScenarios = new Map();
const issuer = process.env.JWT_ISSUER || 'http://mock-provider:9099';
const keys = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
const publicJwk = keys.publicKey.export({ format: 'jwk' });
publicJwk.kid = 'local-development-key'; publicJwk.use = 'sig'; publicJwk.alg = 'RS256';
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
function token(clientId, scope) {
  const now = Math.floor(Date.now() / 1000);
  const header = encode({ alg: 'RS256', typ: 'JWT', kid: publicJwk.kid });
  const payload = encode({ iss: issuer, aud: 'activation-service-api', sub: clientId, client_id: clientId, scope, iat: now, exp: now + 3600 });
  const signature = crypto.sign('RSA-SHA256', Buffer.from(`${header}.${payload}`), keys.privateKey).toString('base64url');
  return `${header}.${payload}.${signature}`;
}

function json(response, status, body, headers = {}) {
  response.writeHead(status, { 'content-type': 'application/json', ...headers });
  response.end(JSON.stringify(body));
}

http.createServer((request, response) => {
  const url = new URL(request.url, 'http://localhost');
  if (request.method === 'GET' && url.pathname === '/health') return json(response, 200, { status: 'UP' });
  if (request.method === 'GET' && url.pathname === '/.well-known/jwks.json') return json(response, 200, { keys: [publicJwk] });
  if (request.method === 'GET' && url.pathname === '/dev/token') {
    return json(response, 200, { access_token: token(url.searchParams.get('clientId') || 'local-client', url.searchParams.get('scope') || 'activation:read activation:write'), token_type: 'Bearer', expires_in: 3600 });
  }
  if (request.method === 'GET' && url.pathname === '/stats') return json(response, 200, { requests: requestCount, activeScenario, productScenarios: Object.fromEntries(productScenarios), idempotencyKeys: Object.fromEntries(calls) });
  if (request.method === 'POST' && url.pathname === '/control/scenario') {
    let body = '';
    request.on('data', chunk => { body += chunk; });
    request.on('end', () => {
      try {
        const scenario = JSON.parse(body).scenario;
        const allowed = ['success', 'bad-request', 'always-500', 'server-error-once', 'rate-limited', 'rate-limited-once', 'unavailable', 'unavailable-once', 'timeout'];
        if (!allowed.includes(scenario)) return json(response, 400, { error: 'invalid_scenario' });
        activeScenario = scenario;
        return json(response, 200, { activeScenario });
      } catch { return json(response, 400, { error: 'invalid_payload' }); }
    });
    return;
  }
  if (request.method === 'POST' && url.pathname === '/control/reset') {
    activeScenario = 'success'; requestCount = 0; calls.clear(); scenarioCalls.clear(); productScenarios.clear();
    return json(response, 200, { activeScenario, requests: requestCount });
  }
  if (request.method === 'POST' && url.pathname === '/control/products') {
    let body = '';
    request.on('data', chunk => { body += chunk; });
    request.on('end', () => {
      try {
        const products = JSON.parse(body).products;
        const allowed = ['success', 'bad-request', 'always-500', 'server-error-once', 'rate-limited', 'rate-limited-once', 'unavailable', 'unavailable-once', 'timeout'];
        if (!products || Object.values(products).some(scenario => !allowed.includes(scenario))) return json(response, 400, { error: 'invalid_product_scenarios' });
        productScenarios.clear(); Object.entries(products).forEach(([product, scenario]) => productScenarios.set(product, scenario));
        return json(response, 200, { productScenarios: Object.fromEntries(productScenarios) });
      } catch { return json(response, 400, { error: 'invalid_payload' }); }
    });
    return;
  }
  if (request.method !== 'POST' || url.pathname !== '/provider/v1/activate') return json(response, 404, { error: 'not_found' });

  requestCount += 1;
  const key = request.headers['idempotency-key'];
  if (!key) return json(response, 400, { error: 'missing_idempotency_key' });
  calls.set(key, (calls.get(key) || 0) + 1);
  let body = '';
  request.on('data', chunk => { body += chunk; });
  request.on('end', () => {
  let productCode;
  try { productCode = JSON.parse(body).productCode; } catch { productCode = undefined; }
  const scenario = request.headers['x-provider-scenario'] || productScenarios.get(productCode) || activeScenario;
  const scenarioKey = `${scenario}:${key}`;
  const scenarioAttempt = (scenarioCalls.get(scenarioKey) || 0) + 1;
  scenarioCalls.set(scenarioKey, scenarioAttempt);
    if (scenario === 'bad-request') return json(response, 400, { error: 'invalid_product' });
    if (scenario === 'server-error' || scenario === 'always-500') return json(response, 500, { error: 'temporary_failure' });
    if (scenario === 'server-error-once' && scenarioAttempt === 1) return json(response, 500, { error: 'temporary_failure' });
    if (scenario === 'rate-limited') return json(response, 429, { error: 'too_many_requests' }, { 'retry-after': '3' });
    if (scenario === 'rate-limited-once' && scenarioAttempt === 1) return json(response, 429, { error: 'too_many_requests' }, { 'retry-after': '3' });
    if (scenario === 'unavailable') return json(response, 503, { error: 'unavailable' }, { 'retry-after': new Date(Date.now() + 3000).toUTCString() });
    if (scenario === 'unavailable-once' && scenarioAttempt === 1) return json(response, 503, { error: 'unavailable' }, { 'retry-after': new Date(Date.now() + 3000).toUTCString() });
    if (scenario === 'timeout') return setTimeout(() => json(response, 200, { activated: true }), 10000);
    return json(response, 200, { activated: true, idempotencyKey: key });
  });
}).listen(9099, '0.0.0.0');
