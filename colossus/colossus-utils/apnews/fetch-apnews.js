const { execFileSync } = require('child_process');
const cheerio = require('cheerio');
const fs = require('fs');

const AP_NEWS_URL = 'https://apnews.com';
const ONE_DAY_MS = 24 * 60 * 60 * 1000;

const outputFile = process.argv[2] || 'apnews-articles.json';
const textFile = process.argv[3] || null;

async function fetchApNewsArticles() {
  const now = Date.now();
  const cutoff = now - ONE_DAY_MS;

  try {
    const html = execFileSync('PLAYWRIGHT', [AP_NEWS_URL], { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 });
    const $ = cheerio.load(html);
    const articles = [];
    const seenUrls = new Set();

    $('div.PagePromo, div.PagePromoTrending').each((_, el) => {
      const $el = $(el);
      const postedTs = parseInt($el.attr('data-posted-date-timestamp'));

      if (!postedTs || postedTs < cutoff) return;

      const $link = $el.find('.PagePromo-title a').first();
      const href = $link.attr('href');
      if (!href) return;

      const url = href.startsWith('http') ? href : `${AP_NEWS_URL}${href}`;
      if (seenUrls.has(url)) return;
      seenUrls.add(url);

      const title = $el.find('.PagePromoContentIcons-text').first().text().trim()
        || $link.text().trim()
        || 'Untitled';

      const updatedTs = parseInt($el.attr('data-updated-date-timestamp')) || null;

      articles.push({
        title,
        url,
        postedAt: new Date(postedTs).toISOString(),
        updatedAt: updatedTs ? new Date(updatedTs).toISOString() : null,
      });
    });

    articles.sort((a, b) => new Date(b.postedAt) - new Date(a.postedAt));

    const outputData = {
      fetchedAt: new Date(now).toISOString(),
      cutoffAt: new Date(cutoff).toISOString(),
      articleCount: articles.length,
      articles,
    };

    fs.writeFileSync(outputFile, JSON.stringify(outputData, null, 2));
    console.log(`written to ${outputFile}, ${articles.length} articles found`);

    if (textFile) {
      $('script, style').remove();
      const text = $('body').text().replace(/\s+/g, ' ').trim();
      fs.writeFileSync(textFile, text);
      console.log(`page text written to ${textFile}`);
    }

    return articles;

  } catch (error) {
    console.error('Error fetching AP News:', error.message);
    throw error;
  }
}

fetchApNewsArticles().catch(console.error);
