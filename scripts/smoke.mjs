// Black-box HTTP smoke: login -> search -> book (needs approval) -> approve -> check-in -> checkout -> audit logs.
// Needs the seeded accounts of domain.md section 13 and works against any profile (no test-support, real clock).
const BASE = (process.env.BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const V1 = `${BASE}/api/v1`;

function fail(message) {
  console.error(`SMOKE FAILED: ${message}`);
  process.exit(1);
}

function send(method, url, { token, body } = {}) {
  return fetch(url, {
    method,
    headers: {
      ...(token && { Authorization: `Bearer ${token}` }),
      ...(body !== undefined && { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(10_000),
  });
}

async function call(method, path, { token, body, expect: expected }) {
  let res;
  try {
    res = await send(method, `${V1}${path}`, { token, body });
  } catch (e) {
    fail(`${method} ${path}: cannot reach ${BASE} (${e.message})`);
  }
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (res.status !== expected) fail(`${method} ${path}: expected ${expected}, got ${res.status} ${text.slice(0, 300)}`);
  return json;
}

function check(condition, message) {
  if (!condition) fail(message);
}

async function login(email, password) {
  const auth = await call('POST', '/auth/login', { body: { email, password }, expect: 200 });
  check(auth.accessToken && auth.user?.email === email, `login ${email}: unexpected response`);
  return auth.accessToken;
}

// Under profile e2e a previous run may have left the clock pinned; the smoke needs real time. Absent elsewhere (ignored).
await send('POST', `${BASE}/test-support/clock`, { body: { reset: true } }).catch(() => undefined);

const employee = await login('employee@spacebooking.local', 'Employee@12345');
const manager = await login('manager@spacebooking.local', 'Manager@12345');
const admin = await login('admin@spacebooking.local', 'Admin@12345');

const rooms = await call('GET', '/rooms?minCapacity=1', { token: employee, expect: 200 });
const approvalRooms = rooms.filter((r) => r.requiresApproval && r.active);
check(approvalRooms.length > 0, 'no active requires_approval room in the room search');

// Start two minutes after the current minute: check-in opens 15 minutes earlier and approve needs now < start.
const startMs = Math.floor(Date.now() / 60_000) * 60_000 + 2 * 60_000;
const slot = { startTime: new Date(startMs).toISOString(), endTime: new Date(startMs + 30 * 60_000).toISOString() };
const title = `Smoke ${new Date(startMs).toISOString()}`;

let booking;
for (const room of approvalRooms) {
  const res = await send('POST', `${V1}/bookings`, { token: employee, body: { roomId: room.id, title, ...slot } });
  if (res.status === 409) continue; // a leftover booking holds this room; try the next one
  const text = await res.text();
  check(res.status === 201, `POST /bookings: expected 201, got ${res.status} ${text.slice(0, 300)}`);
  booking = JSON.parse(text);
  break;
}
check(booking, 'every requires_approval room is already booked for the smoke slot');
check(booking.status === 'PENDING', `new booking in an approval room: expected PENDING, got ${booking.status}`);

const pending = await call('GET', '/bookings/pending?size=50', { token: manager, expect: 200 });
check(pending.content.some((b) => b.id === booking.id), 'manager does not see the booking in /bookings/pending');

const approved = await call('PATCH', `/bookings/${booking.id}/approve`, { token: manager, expect: 200 });
check(approved.status === 'CONFIRMED', `approve: expected CONFIRMED, got ${approved.status}`);

const checkedIn = await call('PATCH', `/bookings/${booking.id}/check-in`, { token: employee, expect: 200 });
check(checkedIn.status === 'CHECKED_IN', `check-in: expected CHECKED_IN, got ${checkedIn.status}`);

const done = await call('PATCH', `/bookings/${booking.id}/checkout`, { token: employee, expect: 200 });
check(done.status === 'COMPLETED', `checkout: expected COMPLETED, got ${done.status}`);

const ownLogs = await call('GET', `/bookings/${booking.id}/logs`, { token: employee, expect: 200 });
const audit = await call('GET', `/audit-logs?bookingId=${booking.id}&size=50`, { token: admin, expect: 200 });
const actions = audit.content.map((l) => l.action).sort();
const wanted = ['APPROVED', 'CHECKED_IN', 'CHECKED_OUT', 'CREATED'];
check(
  JSON.stringify(actions) === JSON.stringify(wanted),
  `audit-logs for the booking: expected ${wanted.join(',')}, got ${actions.join(',')}`,
);
check(Array.isArray(ownLogs) && ownLogs.length === wanted.length, `/bookings/{id}/logs: expected ${wanted.length} rows`);

console.log('SMOKE OK');
