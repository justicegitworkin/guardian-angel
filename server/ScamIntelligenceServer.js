'use strict';

const express    = require('express');
const cors       = require('cors');
const cron       = require('node-cron');
const fetch      = require('node-fetch');
const xml2js     = require('xml2js');
const Database   = require('better-sqlite3');
const Anthropic  = require('@anthropic-ai/sdk');

const PORT              = process.env.PORT || 3000;
const ANTHROPIC_API_KEY = process.env.ANTHROPIC_API_KEY;
const DOLLARS_PER_SCAM  = 1400;

// ── Database ──────────────────────────────────────────────────────────────────
const db = new Database(process.env.DB_PATH || 'scam_intel.db');

db.exec(`
  CREATE TABLE IF NOT EXISTS feed_items (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    guid         TEXT    UNIQUE NOT NULL,
    title        TEXT    NOT NULL DEFAULT '',
    description  TEXT    NOT NULL DEFAULT '',
    link         TEXT    NOT NULL DEFAULT '',
    source       TEXT    NOT NULL DEFAULT '',
    published_at INTEGER NOT NULL DEFAULT 0,
    processed    INTEGER NOT NULL DEFAULT 0
  );

  CREATE TABLE IF NOT EXISTS scam_rules (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    scam_type             TEXT    NOT NULL,
    key_phrases           TEXT    NOT NULL DEFAULT '[]',
    urgency_indicators    TEXT    NOT NULL DEFAULT '[]',
    impersonation_targets TEXT    NOT NULL DEFAULT '[]',
    plain_english_warning TEXT    NOT NULL,
    severity              TEXT    NOT NULL DEFAULT 'MEDIUM',
    created_at            INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
  );

  CREATE TABLE IF NOT EXISTS stats (
    key   TEXT    PRIMARY KEY,
    value INTEGER NOT NULL DEFAULT 0
  );
`);

// Seed stat counters on first run
const initStat = db.prepare('INSERT OR IGNORE INTO stats (key, value) VALUES (?, 0)');
['total_scams_blocked', 'total_calls_screened', 'total_texts_caught', 'total_dollars_protected']
  .forEach(k => initStat.run(k));

// ── RSS Feeds ─────────────────────────────────────────────────────────────────
const FEEDS = [
  { url: 'https://www.fbi.gov/investigate/cyber/alerts/RSS',   source: 'FBI'  },
  { url: 'https://www.ic3.gov/rss',                            source: 'IC3'  },
  { url: 'https://consumer.ftc.gov/consumer-alerts/rss',       source: 'FTC'  },
  { url: 'https://www.cisa.gov/uscert/ncas/alerts.xml',        source: 'CISA' },
];

// ── Claude API ────────────────────────────────────────────────────────────────
const anthropic = ANTHROPIC_API_KEY ? new Anthropic({ apiKey: ANTHROPIC_API_KEY }) : null;

async function extractScamPattern(title, description) {
  if (!anthropic) throw new Error('No ANTHROPIC_API_KEY set');

  const prompt =
    'You are a scam pattern extractor for a senior protection app. ' +
    'Extract actionable detection patterns from this government alert. ' +
    'Respond with JSON only:\n' +
    '{\n' +
    '  "scam_type": "string",\n' +
    '  "target_demographic": "string",\n' +
    '  "key_phrases": ["array of phrases that indicate this scam"],\n' +
    '  "urgency_indicators": ["words/phrases scammers use"],\n' +
    '  "impersonation_targets": ["who they pretend to be"],\n' +
    '  "requested_actions": ["what they ask victims to do"],\n' +
    '  "plain_english_warning": "one sentence for seniors",\n' +
    '  "severity": "LOW|MEDIUM|HIGH|CRITICAL"\n' +
    '}\n\n' +
    `Alert title: ${title}\n` +
    `Alert description: ${String(description).substring(0, 2000)}`;

  const msg = await anthropic.messages.create({
    model:      'claude-haiku-4-5-20251001',
    max_tokens: 512,
    messages:   [{ role: 'user', content: prompt }],
  });

  const text  = msg.content[0].text;
  const match = text.match(/\{[\s\S]*?\}/);
  if (!match) throw new Error('No JSON found in response');
  return JSON.parse(match[0]);
}

// ── Feed parsing (supports RSS 2.0 and Atom) ──────────────────────────────────
async function parseFeed(xmlText) {
  const parsed = await xml2js.parseStringPromise(xmlText, {
    explicitArray: false,
    ignoreAttrs:   false,
  });

  const items = [];

  // RSS 2.0
  const rssItems = parsed?.rss?.channel?.item;
  if (rssItems) {
    const list = Array.isArray(rssItems) ? rssItems : [rssItems];
    list.forEach(item => {
      items.push({
        guid:        String(item.guid?._ || item.guid || item.link || item.title || ''),
        title:       String(item.title || ''),
        description: String(item.description || item.summary || ''),
        link:        String(item.link || ''),
      });
    });
  }

  // Atom
  const atomEntries = parsed?.feed?.entry;
  if (atomEntries) {
    const list = Array.isArray(atomEntries) ? atomEntries : [atomEntries];
    list.forEach(entry => {
      const linkHref = entry.link?.$ ? entry.link.$.href : (entry.link || '');
      items.push({
        guid:        String(entry.id || linkHref || entry.title || ''),
        title:       String(entry.title?._ || entry.title || ''),
        description: String(entry.summary?._ || entry.summary || entry.content?._ || entry.content || ''),
        link:        String(linkHref),
      });
    });
  }

  return items;
}

// ── Prepared statements ───────────────────────────────────────────────────────
const stmtInsertItem = db.prepare(`
  INSERT OR IGNORE INTO feed_items (guid, title, description, link, source, published_at)
  VALUES (@guid, @title, @description, @link, @source, @publishedAt)
`);

const stmtGetUnprocessed = db.prepare(
  'SELECT * FROM feed_items WHERE processed = 0 ORDER BY id LIMIT 20'
);
const stmtMarkProcessed  = db.prepare('UPDATE feed_items SET processed = 1 WHERE id = ?');

const stmtInsertRule = db.prepare(`
  INSERT INTO scam_rules
    (scam_type, key_phrases, urgency_indicators, impersonation_targets, plain_english_warning, severity, created_at)
  VALUES
    (@scamType, @keyPhrases, @urgencyIndicators, @impersonationTargets, @plainEnglishWarning, @severity, @createdAt)
`);

// ── Sync logic ────────────────────────────────────────────────────────────────
async function fetchFeed({ url, source }) {
  try {
    const res = await fetch(url, {
      timeout: 15_000,
      headers: { 'User-Agent': 'GuardianAngel-ScamIntel/1.0' },
    });
    if (!res.ok) {
      console.warn(`[${source}] HTTP ${res.status}`);
      return 0;
    }
    const xml   = await res.text();
    const items = await parseFeed(xml);
    let  stored = 0;
    items.forEach(item => {
      if (!item.guid) return;
      stmtInsertItem.run({
        guid:        item.guid.substring(0, 500),
        title:       item.title.substring(0, 500),
        description: item.description.substring(0, 4000),
        link:        item.link.substring(0, 500),
        source,
        publishedAt: Date.now(),
      });
      stored++;
    });
    console.log(`[${source}] Stored ${stored} feed items`);
    return stored;
  } catch (err) {
    console.error(`[${source}] Feed fetch failed: ${err.message}`);
    return 0;
  }
}

async function processQueue() {
  if (!anthropic) {
    console.warn('Skipping processing — ANTHROPIC_API_KEY not set');
    return;
  }
  const items = stmtGetUnprocessed.all();
  if (items.length === 0) return;

  console.log(`Processing ${items.length} unprocessed items...`);
  for (const item of items) {
    try {
      const pattern = await extractScamPattern(item.title, item.description);
      stmtInsertRule.run({
        scamType:             String(pattern.scam_type              || 'Unknown'),
        keyPhrases:           JSON.stringify(pattern.key_phrases          || []),
        urgencyIndicators:    JSON.stringify(pattern.urgency_indicators   || []),
        impersonationTargets: JSON.stringify(pattern.impersonation_targets|| []),
        plainEnglishWarning:  String(pattern.plain_english_warning  || item.title),
        severity:             String(pattern.severity               || 'MEDIUM'),
        createdAt:            Date.now(),
      });
      console.log(`  ✓ [${pattern.severity}] ${pattern.scam_type}: ${pattern.plain_english_warning}`);
    } catch (err) {
      console.error(`  ✗ Failed to process item ${item.id}: ${err.message}`);
    }
    // Always mark processed (even on error) to avoid infinite retries
    stmtMarkProcessed.run(item.id);
    // Respect Claude API rate limits
    await new Promise(r => setTimeout(r, 800));
  }
}

async function runFullSync() {
  console.log(`\n[${new Date().toISOString()}] ── Scam intelligence sync ─────────`);
  for (const feed of FEEDS) {
    await fetchFeed(feed);
  }
  await processQueue();
  console.log(`── Sync complete ──────────────────────────────────────────────\n`);
}

// ── REST API ──────────────────────────────────────────────────────────────────
const app = express();
app.use(cors());
app.use(express.json({ limit: '10kb' }));

/**
 * GET /scam-rules?since=<unix_ms>
 * Returns all rules created after `since` (0 = all time).
 * Android app polls this every 60 min.
 */
app.get('/scam-rules', (req, res) => {
  const since = parseInt(req.query.since, 10) || 0;
  const rows  = db.prepare(
    'SELECT * FROM scam_rules WHERE created_at > ? ORDER BY created_at DESC LIMIT 200'
  ).all(since);

  res.json({
    rules: rows.map(r => ({
      id:                    r.id,
      scam_type:             r.scam_type,
      key_phrases:           safeParse(r.key_phrases),
      urgency_indicators:    safeParse(r.urgency_indicators),
      impersonation_targets: safeParse(r.impersonation_targets),
      plain_english_warning: r.plain_english_warning,
      severity:              r.severity,
      created_at:            r.created_at,
    })),
  });
});

/**
 * GET /scam-stats
 * Returns aggregate counters for the public tracker dashboard.
 */
app.get('/scam-stats', (req, res) => {
  const statsRows = db.prepare('SELECT key, value FROM stats').all();
  const stats     = Object.fromEntries(statsRows.map(r => [r.key, r.value]));

  stats.total_rules    = db.prepare('SELECT COUNT(*) AS c FROM scam_rules').get().c;
  stats.critical_rules = db.prepare("SELECT COUNT(*) AS c FROM scam_rules WHERE severity='CRITICAL'").get().c;
  stats.high_rules     = db.prepare("SELECT COUNT(*) AS c FROM scam_rules WHERE severity='HIGH'").get().c;
  stats.last_updated   = db.prepare('SELECT MAX(created_at) AS t FROM scam_rules').get().t || 0;

  res.json(stats);
});

/**
 * POST /scam-report
 * Anonymous telemetry from the Android app.
 * Body: { type: "SMS_SCAM"|"SMS_WARNING"|"CALL_SCREENED"|"CALL_SCAM", severity: string }
 */
app.post('/scam-report', (req, res) => {
  const { type } = req.body || {};
  if (!type) return res.status(400).json({ error: 'missing type' });

  const incr = db.prepare('UPDATE stats SET value = value + ? WHERE key = ?');

  if (type === 'SMS_WARNING' || type === 'SMS_SCAM') {
    incr.run(1, 'total_texts_caught');
  }
  if (type === 'CALL_SCREENED') {
    incr.run(1, 'total_calls_screened');
  }
  if (type === 'SMS_SCAM' || type === 'CALL_SCAM') {
    incr.run(1,               'total_scams_blocked');
    incr.run(DOLLARS_PER_SCAM,'total_dollars_protected');
  }

  res.json({ ok: true });
});

/** GET /health — used by Railway/Render health checks */
app.get('/health', (_req, res) => res.json({ status: 'ok', version: '1.0.0' }));

// ── Helpers ───────────────────────────────────────────────────────────────────
function safeParse(str) {
  try { return JSON.parse(str || '[]'); } catch { return []; }
}

// ── Start ─────────────────────────────────────────────────────────────────────
app.listen(PORT, () => {
  console.log(`Guardian Angel Scam Intelligence Server listening on port ${PORT}`);
  if (!ANTHROPIC_API_KEY) {
    console.warn('WARNING: ANTHROPIC_API_KEY is not set — feed items will be stored but not processed into rules');
  }
  // Initial sync 10 s after boot to allow the process to settle
  setTimeout(runFullSync, 10_000);
});

// Hourly sync (at the top of every hour)
cron.schedule('0 * * * *', runFullSync);
