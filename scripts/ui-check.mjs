// Scripted UI check (Claude Code) against the running local app. Real browser, real backend, real screenshots.
// Used once on 2026-10-09 for manual-test rows marked "scripted" in docs/EVIDENCE.md section 2.3.
// Needs Google Chrome and puppeteer-core (not a project dependency): in an empty folder run `npm i puppeteer-core@24`,
// copy this file there, start backend + frontend on a freshly seeded database, then:
//   node ui-check.mjs main <screenshotDir>      (stop the backend afterwards)
//   node ui-check.mjs offline <screenshotDir>   (backend must be stopped)
// It changes data (posts, orders, a review), so reset the database before a demo.
import puppeteer from 'puppeteer-core';
import fs from 'node:fs';

const [part, shotDir] = process.argv.slice(2);
const BASE = 'http://localhost:5173';
const log = [];
const note = (id, what, value) => { log.push({ id, what, value }); console.log(id, '|', what, '|', JSON.stringify(value)); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const browser = await puppeteer.launch({ executablePath: '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox'] });

async function newPhone() {
  const ctx = await browser.createBrowserContext(); // fresh storage = fresh cart/login
  const page = await ctx.newPage();
  await page.setViewport({ width: 360, height: 800, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  page.on('pageerror', (e) => note('console', 'page error', e.message));
  return page;
}
const text = (page) => page.evaluate(() => document.querySelector('main')?.innerText ?? document.body.innerText);
const overflow = (page) => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
const shot = async (page, name) => { await sleep(300); await page.screenshot({ path: `${shotDir}/${name}.png` }); note('shot', 'saved', `${name}.png`); };
const go = async (page, path) => { await page.goto(BASE + path, { waitUntil: 'networkidle0' }); await sleep(300); };
const click = async (page, sel) => { await page.locator(sel).click(); await sleep(500); };
const waitText = (page, t, timeout = 8000) => page.waitForFunction((s) => document.body.innerText.includes(s), { timeout }, t);

async function login(page, email) {
  await go(page, '/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill('Password123!');
  await click(page, 'form button[type=submit]');
  await page.waitForFunction(() => !location.pathname.startsWith('/login'));
  await sleep(500);
}
async function selectIn(page, labelText, value) {
  await page.evaluate((labelText, value) => {
    const label = [...document.querySelectorAll('label')].find((l) => l.textContent.trim().startsWith(labelText));
    const el = label.querySelector('select, input');
    const proto = el.tagName === 'SELECT' ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, value);
    el.dispatchEvent(new Event(el.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }));
  }, labelText, value);
}
async function openFilters(page) {
  await page.evaluate(() => { const d = document.querySelector('details'); if (d) d.open = true; });
  await sleep(200);
}

if (part === 'main') {
  // ---------- M3-02 price filters (visitor) ----------
  let p = await newPhone();
  await go(p, '/');
  await openFilters(p);
  await selectIn(p, 'Condition', 'NEW');
  await selectIn(p, 'Max price', '130');
  await click(p, '::-p-text(Search)');
  await sleep(800);
  note('M3-02', 'url', p.url());
  note('M3-02', 'cards', await p.$$eval('main a .card-title, main .listing-card h3, main article h3, main h3', (els) => els.map((e) => e.textContent.trim())));
  note('M3-02', 'text', (await text(p)).slice(0, 900));
  await openFilters(p);
  await shot(p, 'M3-02-new-under-130');
  await selectIn(p, 'Min price', '500');
  await selectIn(p, 'Max price', '100');
  const urlBefore = p.url();
  await click(p, '::-p-text(Search)');
  await sleep(800);
  note('M3-02', 'url unchanged after min>max', p.url() === urlBefore);
  note('M3-02', 'alert', await p.$eval('[role=alert]', (e) => e.textContent).catch(() => 'NO ALERT'));
  await shot(p, 'M3-02-min-above-max');

  // ---------- M3-03 parts 1-2: grid width and empty result ----------
  await go(p, '/');
  note('M3-03', 'overflow px (home, all listings)', await overflow(p));
  note('M3-03', 'grid columns', await p.evaluate(() => { const g = document.querySelector('.listing-grid, .grid, main ul'); return g ? getComputedStyle(g).gridTemplateColumns : 'no grid element'; }));
  await shot(p, 'M3-03-home-grid');
  await openFilters(p);
  note('M3-03', 'overflow px (filters open)', await overflow(p));
  await shot(p, 'M3-03-filters-open');
  await p.locator('input[aria-label="Search listings"]').fill('zzzunicorn');
  await click(p, '::-p-text(Search)');
  await sleep(800);
  note('M3-03', 'empty text', (await text(p)).slice(0, 600));
  await shot(p, 'M3-03-no-results');

  // ---------- M6-01 bulletin as visitor ----------
  await go(p, '/bulletin');
  note('M6-01', 'post titles', await p.$$eval('.post .line-title', (els) => els.map((e) => e.textContent.trim())));
  await shot(p, 'M6-01-bulletin-all');
  await click(p, '::-p-text(Event)');
  await sleep(800);
  note('M6-01', 'event titles', await p.$$eval('.post .line-title', (els) => els.map((e) => e.textContent.trim())));
  await shot(p, 'M6-01-bulletin-events');

  // ---------- M6-02 / M6-03 as Lerato ----------
  p = await newPhone();
  await login(p, 'lerato@mycput.ac.za');
  await go(p, '/bulletin/new');
  await click(p, 'form button[type=submit]');
  await sleep(500);
  note('M6-02', 'empty form errors', await p.$$eval('.field-error, [role=alert]', (els) => els.map((e) => e.textContent.trim())));
  await shot(p, 'M6-02-empty-form');
  await p.locator('#title').fill('Study session in the library on Friday');
  await p.select('#category', 'EVENT');
  await p.locator('#body').fill('Join us at 14:00 on the 2nd floor of the library to revise for exams together.');
  await click(p, 'form button[type=submit]');
  await p.waitForFunction(() => /^\/bulletin\/\d+$/.test(location.pathname));
  await sleep(500);
  note('M6-02', 'post page', { url: p.url(), text: (await text(p)).slice(0, 500) });
  await shot(p, 'M6-02-post-published');
  await click(p, '::-p-text(Delete)');
  note('M6-03', 'confirm text', (await text(p)).slice(-200));
  await shot(p, 'M6-03-delete-confirm');
  await click(p, '.btn-danger');
  await sleep(800);
  note('M6-03', 'after delete url', p.url());
  note('M6-03', 'titles after delete', await p.$$eval('.post .line-title', (els) => els.map((e) => e.textContent.trim())));
  await shot(p, 'M6-03-after-delete');
  await go(p, '/bulletin/3'); // Thabo's? check author in text
  note('M6-03', 'other post text', (await text(p)).slice(0, 400));
  note('M6-03', 'delete button on other post', await p.$$eval('button', (b) => b.some((x) => x.textContent.trim() === 'Delete')));
  await shot(p, 'M6-03-other-post-no-delete');

  // ---------- M4 as Ayesha ----------
  p = await newPhone();
  await login(p, 'ayesha@mycput.ac.za');
  await go(p, '/listings/3');
  await click(p, '::-p-text(Add to cart)');
  note('M4-01', 'header cart label', await p.$eval('a[href="/cart"]', (e) => e.getAttribute('aria-label') + ' / ' + e.textContent.trim()));
  await go(p, '/cart');
  note('M4-01', 'cart text', (await text(p)).slice(0, 500));
  await shot(p, 'M4-01-cart-one-item');
  await p.reload({ waitUntil: 'networkidle0' });
  await sleep(500);
  note('M4-01', 'cart after reload', (await text(p)).slice(0, 300));
  await shot(p, 'M4-01-cart-after-reload');

  await go(p, '/listings/4');
  await click(p, '::-p-text(Add to cart)');
  note('M4-02', 'header after 2nd Thabo item', await p.$eval('a[href="/cart"]', (e) => e.getAttribute('aria-label')));
  await go(p, '/listings/11');
  await click(p, '::-p-text(Add to cart)');
  note('M4-02', 'message for other seller', (await text(p)).slice(0, 700));
  await shot(p, 'M4-02-other-seller-refused');
  note('M4-02', 'header after refused', await p.$eval('a[href="/cart"]', (e) => e.getAttribute('aria-label')));

  await go(p, '/cart');
  note('M4-06', 'cart overflow px', await overflow(p));
  await shot(p, 'M4-06-cart-two-items');
  await click(p, '::-p-text(Checkout)');
  await sleep(500);
  note('M4-06', 'checkout overflow px', await overflow(p));
  note('M4-06', 'pay button width vs card', await p.$eval('form button[type=submit]', (b) => ({ button: b.offsetWidth, form: b.parentElement.closest('form').clientWidth, height: b.offsetHeight })));
  await p.locator('#cardNumber').fill('4000 0000 0000 0002');
  await shot(p, 'M4-03-checkout-decline-card');
  await click(p, 'form button[type=submit]');
  await sleep(1500);
  note('M4-03', 'decline message', await p.$eval('[role=alert]', (e) => e.textContent).catch(() => 'NO ALERT'));
  note('M4-03', 'url', p.url());
  await shot(p, 'M4-03-declined');
  note('M4-03', 'cart kept (header)', await p.$eval('a[href="/cart"]', (e) => e.getAttribute('aria-label')));

  await p.locator('#cardNumber').fill('4242 4242 4242 4242');
  await click(p, 'form button[type=submit]');
  await p.waitForFunction(() => /^\/orders\/\d+$/.test(location.pathname), { timeout: 10000 });
  await sleep(800);
  note('M4-04', 'order page', { url: p.url(), text: (await text(p)).slice(0, 700) });
  note('M4-04', 'header cart', await p.$eval('a[href="/cart"]', (e) => e.getAttribute('aria-label')));
  note('M4-06', 'order overflow px', await overflow(p));
  await shot(p, 'M4-04-paid');
  const orderUrl = p.url();
  await go(p, '/listings/3');
  note('M4-04', 'lamp page', (await text(p)).slice(0, 300));
  await shot(p, 'M4-04-lamp-sold');
  await go(p, '/?q=lamp');
  await sleep(500);
  note('M4-04', 'search lamp after sale', (await text(p)).slice(0, 600));
  await shot(p, 'M4-04-search-no-lamp');

  await p.goto(orderUrl, { waitUntil: 'networkidle0' });
  await click(p, '::-p-text(I received my order)');
  await sleep(800);
  note('M4-05', 'after confirm', (await text(p)).slice(0, 700));
  await shot(p, 'M4-05-completed');

  const thabo = await newPhone();
  await login(thabo, 'thabo@mycput.ac.za');
  await go(thabo, '/listings/1');
  note('M4-05', 'Thabo own listing buttons', await thabo.$$eval('main button', (b) => b.map((x) => x.textContent.trim())));
  await shot(thabo, 'M4-05-own-listing-no-add-to-cart');

  // ---------- M5 as Ayesha buying Lerato's wireless mouse ----------
  await go(p, '/listings/11');
  await click(p, '::-p-text(Add to cart)');
  await go(p, '/checkout');
  await p.locator('#cardNumber').fill('4242 4242 4242 4242');
  await click(p, 'form button[type=submit]');
  await p.waitForFunction(() => /^\/orders\/\d+$/.test(location.pathname), { timeout: 10000 });
  await sleep(800);
  await click(p, '::-p-text(I received my order)');
  await sleep(800);
  note('M5-01', 'review form', (await text(p)).slice(0, 900));
  await shot(p, 'M5-01-review-form');
  note('M5-04', 'order+review overflow px', await overflow(p));
  note('M5-04', 'star tap targets', await p.$$eval('.star-input label', (ls) => ls.map((l) => `${l.offsetWidth}x${l.offsetHeight}`)));

  await click(p, '::-p-text(Submit review)');
  await sleep(500);
  note('M5-02', 'no rating message', await p.$eval('.star-input [role=alert]', (e) => e.textContent).catch(() => 'NO MESSAGE'));
  await shot(p, 'M5-02-no-rating');
  await p.evaluate(() => document.querySelector('input[name=rating][value="4"]').click());
  await p.locator('#comment').fill('Mouse works perfectly, quick hand-over at the library.');
  await shot(p, 'M5-04-review-form-filled');
  await click(p, '::-p-text(Submit review)');
  await sleep(1000);
  note('M5-02', 'after submit', (await text(p)).slice(-400));
  await shot(p, 'M5-02-review-saved');

  await go(p, '/listings/12');
  note('M5-03', 'Lerato listing rating line', (await text(p)).slice(0, 400));
  await shot(p, 'M5-03-listing-rating');
  await click(p, '::-p-text(Lerato Mokoena)');
  await sleep(800);
  note('M5-03', 'Lerato seller page', (await text(p)).slice(0, 600));
  await shot(p, 'M5-03-seller-page');
  await go(p, '/profile');
  note('M5-03', 'Ayesha profile', (await text(p)).slice(0, 600));
  await shot(p, 'M5-03-ayesha-profile');
}

if (part === 'offline') {
  const p = await newPhone();
  await go(p, '/status');
  await sleep(1500);
  note('M0-02', 'status page backend down', (await text(p)).slice(0, 400));
  await shot(p, 'M0-02-server-unreachable');
  await go(p, '/');
  await p.locator('input[aria-label="Search listings"]').fill('lamp');
  await click(p, '::-p-text(Search)');
  await sleep(2000);
  note('M3-03', 'search backend down', (await text(p)).slice(0, 500));
  note('M3-03', 'try again button', await p.$$eval('button', (b) => b.some((x) => /try again/i.test(x.textContent))));
  await shot(p, 'M3-03-server-unreachable');
}

fs.writeFileSync(`${shotDir}/../ui-check-${part}.json`, JSON.stringify(log, null, 2));
await browser.close();
