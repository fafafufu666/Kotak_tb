// End-to-end check of the relay against a throwaway database:
//   node --disable-warning=ExperimentalWarning test.mjs
import { spawn } from 'node:child_process';
import assert from 'node:assert/strict';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const PORT = 18787;
const base = `http://127.0.0.1:${PORT}`;
const server = spawn(process.execPath, ['--disable-warning=ExperimentalWarning', new URL('./server.mjs', import.meta.url).pathname], {
  env: { ...process.env, PORT: String(PORT), DB_PATH: join(tmpdir(), `relay-test-${process.pid}.db`), TICK_MS: '200' },
  stdio: ['ignore', 'pipe', 'inherit'],
});
await new Promise((ok) => server.stdout.once('data', ok));

async function api(method, path, token, body) {
  const r = await fetch(base + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: r.status, json: await r.json() };
}

/** Collects SSE messages from the caregiver stream. */
async function listen(token, lastEventId) {
  const ctrl = new AbortController();
  const r = await fetch(base + '/v1/stream', {
    headers: { Authorization: `Bearer ${token}`, ...(lastEventId ? { 'Last-Event-ID': String(lastEventId) } : {}) },
    signal: ctrl.signal,
  });
  const got = [];
  (async () => {
    let buf = '';
    try {
      for await (const chunk of r.body) {
        buf += Buffer.from(chunk).toString();
        let i;
        while ((i = buf.indexOf('\n\n')) >= 0) {
          const block = buf.slice(0, i); buf = buf.slice(i + 2);
          const type = /^event: (.+)$/m.exec(block)?.[1];
          const data = /^data: (.+)$/m.exec(block)?.[1];
          if (type) got.push({ type, data: JSON.parse(data) });
        }
      }
    } catch { /* aborted */ }
  })();
  return { got, close: () => ctrl.abort() };
}
const wait = (ms) => new Promise((ok) => setTimeout(ok, ms));
const fall = (seq) => ({ deviceAddress: 'AA:BB:CC:00:11:22', seq, type: 'FALL', batteryPct: 80, occurredAt: Date.now(), impactG: 3.9, message: 'FALL detected — test' });

try {
  const page = await fetch(base + '/');
  assert.equal(page.status, 200);
  assert.match(page.headers.get('content-type'), /text\/html/);
  assert.match(await page.text(), /Pendamping/, 'PMO web page served');

  const health = (await api('GET', '/v1/health')).json;
  assert.equal(health.service, 'fallbracelet-relay');
  assert.match(health.relayId, /^[0-9a-f]{16}$/);

  const w = (await api('POST', '/v1/wearers', null, { name: 'Grandma' })).json;
  assert.ok(w.token);

  assert.equal((await api('POST', '/v1/link', null, { code: '000000', name: 'X' })).status, 404, 'bad code refused');
  const invite = (await api('POST', '/v1/invites', w.token)).json;
  const c = (await api('POST', '/v1/link', null, { code: invite.code, name: 'Sara' })).json;
  assert.ok(c.token);
  assert.equal(c.wearer.name, 'Grandma');
  assert.equal((await api('POST', '/v1/link', null, { code: invite.code, name: 'Y' })).status, 404, 'invite is single use');
  assert.equal((await api('GET', '/v1/caregivers', w.token)).json.caregivers.length, 1);
  assert.equal((await api('GET', '/v1/dashboard', w.token)).status, 401, 'wearer token is not a caregiver token');

  const s = await listen(c.token);
  await wait(200);
  const first = (await api('POST', '/v1/events', w.token, fall(1))).json;
  assert.equal(first.duplicate, false);
  assert.equal((await api('POST', '/v1/events', w.token, fall(1))).json.duplicate, true, 'resend is a duplicate');
  await api('POST', '/v1/heartbeat', w.token, { status: { state: 'MONITORING', batteryPct: 80 } });
  await wait(300);
  const events = s.got.filter((m) => m.type === 'event');
  assert.equal(events.length, 1, 'caregiver alarmed once, not twice');
  assert.equal(events[0].data.alarm, true);
  assert.equal(events[0].data.wearerName, 'Grandma');
  assert.ok(s.got.some((m) => m.type === 'status'));

  await api('POST', `/v1/events/${first.id}/ack`, c.token);
  await wait(200);
  assert.ok(s.got.some((m) => m.type === 'ack' && m.data.by === 'Sara'));
  const hb = (await api('POST', '/v1/heartbeat', w.token, { status: {}, acksSince: 0 })).json;
  assert.equal(hb.acks[0].acked_by, 'Sara', 'wearer learns who saw it');
  s.close();

  // Missed while disconnected: replayed with Last-Event-ID.
  const second = (await api('POST', '/v1/events', w.token, { ...fall(2), type: 'SOS' })).json;
  const s2 = await listen(c.token, first.id);
  await wait(300);
  assert.ok(s2.got.some((m) => m.type === 'event' && m.data.id === second.id && m.data.replay), 'missed event replayed');
  s2.close();

  const dash = (await api('GET', '/v1/dashboard', c.token)).json;
  assert.equal(dash.wearers[0].events.length, 2);
  assert.equal(dash.wearers[0].events.find((e) => e.id === first.id).ackedBy, 'Sara');

  // --- Medicine box: ESKALASI with the app's re-send timing, daily statuses, "Dilihat oleh" -----

  const p = (await api('POST', '/v1/wearers', null, { name: 'Budi' })).json;
  const inv2 = (await api('POST', '/v1/invites', p.token)).json;
  const pmo = (await api('POST', '/v1/link', null, { code: inv2.code, name: 'Ani' })).json;
  const ps = await listen(pmo.token);
  await wait(200);

  const hari = ['2026-11-18', '2026-11-19', '2026-11-20', '2026-11-21', '2026-11-22', '2026-11-23', '2026-11-24']
    .map((tanggal, i) => ({ tanggal, hariKe: 52 + i, status: i < 4 ? 'DIMINUM_KOTAK' : i < 6 ? 'TERLEWAT' : 'BELUM', waktu: null }));
  const st = (await api('POST', '/v1/status-harian', p.token, {
    tanggal: '2026-11-24', demo: true, hariKe: 58, totalHari: 180, terakhirDibuka: Date.parse('2026-11-21T00:05:00Z'),
    hariRawan: true, terlewatBerturut: 2, hari: [...hari, { tanggal: '2026-11-17', status: 'NONSENSE' }],
  }));
  assert.equal(st.status, 200);
  assert.deepEqual(st.json.eskalasi, [], 'no escalation yet');
  assert.equal((await api('POST', '/v1/status-harian', p.token, { hari })).status, 400, 'tanggal required');

  const eskalasi = (tanggal, seq) => ({
    deviceAddress: 'demo-abc', seq, type: 'ESKALASI', occurredAt: Date.now(), message: 'Teks netral',
    demo: true, ulangKirimMenit: 0.01, maksUlang: 2,
    detail: { tanggal, terlewatBerturut: 2, terakhirDibuka: Date.parse('2026-11-21T00:05:00Z'), hariRawan: true, hariKe: 57 },
  });
  const esk = (await api('POST', '/v1/events', p.token, eskalasi('2026-11-23', 20415))).json;
  assert.equal((await api('POST', '/v1/events', p.token, eskalasi('2026-11-23', 20415))).json.duplicate, true, 'one per date');
  await wait(2500);
  const got = ps.got.filter((m) => m.type === 'event' && m.data.id === esk.id);
  assert.equal(got.length, 3, 'sent once, then re-sent maksUlang (2) times');
  assert.equal(got[0].data.alarm, true);
  assert.equal(got[0].data.demo, true);
  assert.equal(got[0].data.detail.terlewatBerturut, 2);
  assert.ok(got[2].data.escalated);
  assert.ok(ps.got.some((m) => m.type === 'days' && m.data.days.length === 7), 'PMO page hears the 7 days live');

  // A second one, acknowledged at once: no re-sends after "Tandai dilihat".
  const esk2 = (await api('POST', '/v1/events', p.token, eskalasi('2026-11-24', 20416))).json;
  await api('POST', `/v1/events/${esk2.id}/ack`, pmo.token);
  await wait(1500);
  assert.equal(ps.got.filter((m) => m.type === 'event' && m.data.id === esk2.id).length, 1, 'ack stops re-sends');
  ps.close();

  const pd = (await api('GET', '/v1/dashboard', pmo.token)).json.wearers[0];
  assert.equal(pd.name, 'Budi');
  assert.equal(pd.ringkasan.hariKe, 58);
  assert.equal(pd.ringkasan.terakhirDibuka, Date.parse('2026-11-21T00:05:00Z'));
  assert.equal(pd.ringkasan.demo, true);
  assert.deepEqual(pd.days.map((d) => d.status), hari.map((h) => h.status), '7-day row, invalid status dropped');
  assert.equal(pd.events.find((e) => e.id === esk2.id).ackedBy, 'Ani');

  const seen = (await api('POST', '/v1/status-harian', p.token, { tanggal: '2026-11-24', demo: true, hari })).json.eskalasi;
  assert.equal(seen.find((e) => e.id === esk2.id).ackedBy, 'Ani', 'patient learns who saw it');
  assert.equal(seen.find((e) => e.id === esk.id).ackedBy, null);

  // Back to real data: the PMO sees the real days, not the demo ones.
  await api('POST', '/v1/status-harian', p.token, { tanggal: '2026-11-24', demo: false, hari: [{ tanggal: '2026-11-24', hariKe: 1, status: 'BELUM' }] });
  const real = (await api('GET', '/v1/dashboard', pmo.token)).json.wearers[0];
  assert.equal(real.ringkasan.demo, false);
  assert.equal(real.ringkasan.terakhirDibuka, null, 'never opened stays null, not 0');
  assert.equal(real.events.filter((e) => e.demo).length, 0, 'demo ESKALASI dropped once back on real data');

  // An unseen demo ESKALASI stops re-sending once the phone is back on real data.
  const ps2 = await listen(pmo.token);
  await wait(200);
  await api('POST', '/v1/status-harian', p.token, { tanggal: '2026-11-24', demo: true, hari });
  const esk3 = (await api('POST', '/v1/events', p.token, eskalasi('2026-11-25', 20417))).json;
  await api('POST', '/v1/status-harian', p.token, { tanggal: '2026-11-24', demo: false, hari: [] });
  await wait(1500);
  assert.equal(ps2.got.filter((m) => m.type === 'event' && m.data.id === esk3.id).length, 1, 'no re-sends after the demo ended');
  const esk4 = (await api('POST', '/v1/events', p.token, { ...eskalasi('2026-11-26', 20418), deviceAddress: 'demo-def' })).json;
  assert.ok(esk4.id > esk3.id, 'event ids are never reused after demo events are deleted');
  ps2.close();
  assert.deepEqual(real.days.map((d) => d.status), ['BELUM']);

  await api('DELETE', `/v1/caregivers/${c.caregiverId}`, w.token);
  assert.equal((await api('GET', '/v1/dashboard', c.token)).json.wearers.length, 0, 'unlinked caregiver sees nothing');

  console.log('relay tests passed');
} finally {
  server.kill();
}
