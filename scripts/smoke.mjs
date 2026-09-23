// Run only against a local development stack. Creates two isolated test accounts.
import assert from 'node:assert/strict';
const base = process.argv[2] || 'http://localhost:3000';
if (!['localhost', '127.0.0.1', '::1'].includes(new URL(base).hostname))
  throw new Error('This smoke test only runs against localhost.');
function session() {
  let cookies = new Map();
  let token = '';
  return {
    async request(path, method = 'GET', data, expected = 200) {
      const form = data instanceof FormData;
      const response = await fetch(base + '/api' + path, {
        method,
        headers: {
          Cookie: Array.from(cookies)
            .map(([k, v]) => `${k}=${v}`)
            .join('; '),
          ...(method !== 'GET' ? { 'X-CSRF-TOKEN': token } : {}),
          ...(data && !form ? { 'Content-Type': 'application/json' } : {}),
        },
        body: data ? (form ? data : JSON.stringify(data)) : undefined,
      });
      for (const cookie of response.headers.getSetCookie()) {
        const [pair] = cookie.split(';');
        const index = pair.indexOf('=');
        cookies.set(pair.slice(0, index), pair.slice(index + 1));
      }
      const text = await response.text();
      assert.equal(response.status, expected, `${method} ${path}: ${text}`);
      try {
        return JSON.parse(text);
      } catch {
        return text;
      }
    },
    async csrf() {
      token = (await this.request('/auth/csrf')).token;
    },
  };
}
const alice = session(),
  bob = session();
for (const [index, client] of [alice, bob].entries()) {
  await client.csrf();
  await client.request('/auth/register', 'POST', {
    name: 'Smoke test ' + index,
    email: `smoke-${crypto.randomUUID()}@example.invalid`,
    password: 'Disposable-local-test-2026!',
  });
  await client.csrf();
  assert.equal((await client.request('/auth/me')).name, 'Smoke test ' + index);
}
const profile = await alice.request('/profiles', 'POST', {
  name: 'Software Engineering',
  language: 'en',
  text: 'Built Java REST APIs using Spring Boot. German B2.',
  documentId: null,
});
const data = {
  title: 'Smoke-test Engineer',
  company: 'Local Test AG',
  status: 'APPLIED',
  location: 'Zürich',
  canton: 'ZH',
  description: 'Java and Spring Boot. German B2.',
  language: 'en',
  salaryMin: 75000,
  salaryMax: 82000,
  profileId: profile.id,
  interviewDate: '2026-09-28T12:00:00Z',
};
const job = await alice.request('/jobs', 'POST', data);
assert.match(job.appliedDate, /^\d{4}-\d{2}-\d{2}$/);
assert.equal((await bob.request('/jobs')).length, 0);
await bob.request('/jobs/' + job.id, 'PUT', { version: job.version, data }, 404);
const updated = await alice.request('/jobs/' + job.id, 'PUT', {
  version: job.version,
  data: { ...data, status: 'INTERVIEW' },
});
assert.equal(updated.status, 'INTERVIEW');
await alice.request('/jobs/' + job.id, 'PUT', { version: job.version, data }, 409);
const upload = new FormData();
upload.set(
  'file',
  new Blob(['A sample CV for the local smoke test.'], { type: 'text/plain' }),
  'smoke-cv.txt',
);
upload.set('entryId', job.id);
const document = await alice.request('/documents', 'POST', upload);
assert.equal(document.text, 'A sample CV for the local smoke test.');
await bob.request('/documents/' + document.id, 'GET', undefined, 404);
assert.equal(
  await alice.request('/documents/' + document.id),
  'A sample CV for the local smoke test.',
);
const exported = await alice.request('/export');
assert.equal(exported.applications.length, 1);
assert.equal(exported.profiles.length, 1);
await alice.request('/jobs/' + job.id, 'DELETE');
await alice.request('/profiles/' + profile.id, 'DELETE');
assert.equal((await alice.request('/documents')).length, 0);
await alice.request('/auth/logout', 'POST', undefined, 204);
await alice.request('/auth/me', 'GET', undefined, 401);
console.log(
  'PASS: registration, session cookies, CSRF, profiles, applications, status updates, isolation, stale edits, document upload/download, export, deletion and logout.',
);
