const { chromium } = require('playwright');
const cheerio = require('cheerio');

const url = process.argv[2];
const extractText = process.argv[3] === 'true';

if (!url) {
  process.stderr.write('Usage: PLAYWRIGHT <url> [extract-text]\n');
  process.exit(1);
}

(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36',
    extraHTTPHeaders: {
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
      'Accept-Language': 'en-US,en;q=0.9',
    },
  });
  const page = await context.newPage();

  const response = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
  const contentType = (response.headers()['content-type'] || '');
  const content = await page.content();

  if (extractText && contentType.includes('text/html')) {
    const $ = cheerio.load(content);
    $('script, style, noscript').remove();
    process.stdout.write($('body').text().replace(/\s+/g, ' ').trim());
  } else {
    process.stdout.write(content);
  }

  await browser.close();
})().catch(err => {
  process.stderr.write(err.message + '\n');
  process.exit(1);
});
