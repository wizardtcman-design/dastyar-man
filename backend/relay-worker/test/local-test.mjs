/**
 * Local, offline test of the relay Worker's routing and security rules.
 * It imports the Worker directly and calls its fetch() with fake env, so no
 * Cloudflare account, deploy or real key is needed. Run with:
 *
 *    node backend/relay-worker/test/local-test.mjs
 *
 * The upstream calls that do reach the network use deliberately-invalid keys,
 * so they exercise the real forwarding path without spending anyone's credit.
 */
import worker from "../src/index.js";

const env = {
  GATEWAY_TOKEN: "test-token-123",
  OPENROUTER_API_KEY: "invalid-probe-key",
  GROQ_API_KEY: "invalid-probe-key",
  RATE_LIMIT_PER_MIN: "5",
};

async function call(path, { token, method = "GET" } = {}) {
  const headers = new Headers();
  if (token) headers.set("X-Gateway-Token", token);
  const req = new Request("https://relay.example.com" + path, { method, headers });
  const res = await worker.fetch(req, env);
  return { status: res.status, text: await res.text().catch(() => "") };
}

let pass = 0, fail = 0;
function check(name, got, want) {
  const ok = got === want;
  console.log(`${ok ? "PASS" : "FAIL"}  ${name}  (got ${got}, want ${want})`);
  ok ? pass++ : fail++;
}

check("health ok", (await call("/health")).status, 200);
check("no token rejected", (await call("/proxy/api.groq.com/openai/v1/models")).status, 401);
check("wrong token rejected", (await call("/proxy/api.groq.com/openai/v1/models", { token: "x" })).status, 401);
check("disallowed host blocked", (await call("/proxy/evil.com/x", { token: "test-token-123" })).status, 403);
check("unknown path 404", (await call("/nope", { token: "test-token-123" })).status, 404);
check("bad target 400", (await call("/proxy/nohost", { token: "test-token-123" })).status, 400);
check("bad method 405", (await call("/proxy/api.groq.com/openai/v1/models", { token: "test-token-123", method: "DELETE" })).status, 405);

// Rate limit: limit is 5/min, so the 6th allowed call must be 429.
let last = 0;
for (let i = 0; i < 6; i++) {
  last = (await call("/proxy/api.groq.com/openai/v1/models", { token: "test-token-123" })).status;
}
check("rate limited on 6th", last, 429);

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
