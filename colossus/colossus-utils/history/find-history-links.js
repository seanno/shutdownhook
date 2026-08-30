const fs = require('fs');
const path = require('path');
const cheerio = require('cheerio');

const MONTHS = ['january','february','march','april','may','june','july',
                'august','september','october','november','december'];

function todaySlug() {
  const now = new Date();
  return `${MONTHS[now.getMonth()]}-${now.getDate()}`;  // e.g. "august-28"
}

async function fetchHtml(url) {
  const res = await fetch(url, {
    headers: { 'User-Agent': 'Mozilla/5.0 (compatible; TDIH-scraper/1.0)' }
  });
  if (!res.ok) throw new Error(`HTTP ${res.status} for ${url}`);
  return res.text();
}

function scrape(html) {
  const $ = cheerio.load(html);
  const items = [];
  const seen = new Set();

  $('a[href^="/this-day-in-history/"]').each((i, el) => {
    const $a = $(el);
    const href = $a.attr('href');

    // Real events have a 3-segment slug: /this-day-in-history/<month-day>/<name>
    // The 2-segment link is the month index page — skip it.
    if (href.replace(/^\//, '').split('/').length < 3) return;

    // Year: first <span> whose text contains a 4-digit year.
    // (A second span holds reading time like "1:40m read" — must skip it.)
    let year = null;
    $a.find('span').each((j, s) => {
      if (year != null) return;
      const m = $(s).text().match(/\b(1\d{3}|20\d{2})\b/);
      if (m) year = parseInt(m[1], 10);
    });
    if (year == null) return;

    // <p>[0] = title, <p>[1] = summary
    const paras = $a.find('p')
      .map((j, p) => $(p).text().replace(/\s+/g, ' ').trim())
      .get()
      .filter(Boolean);

    const title = paras[0] || '';
    const summary = paras[1] || '';

    // Drop items missing either field (e.g. the hero, which has no <p> summary).
    if (!title || !summary) return;

    const url = `https://www.history.com${href}`;
    if (seen.has(url)) return;
    seen.add(url);

    items.push({ year, title, summary, url });
  });

  return items.sort((a, b) => b.year - a.year);
}

async function main() {
  const slug = todaySlug();

  const url = `https://www.history.com/this-day-in-history/${slug}`;
  const html = await fetchHtml(url);
  const items = scrape(html);

  const outFile = process.argv[2] || 'history-articles.json';
  fs.writeFileSync(outFile, JSON.stringify(items, null, 2));
  console.log(`Wrote ${items.length} items for ${slug} to ${outFile}`);
}

main().catch(err => { console.error(err); process.exit(1); });
