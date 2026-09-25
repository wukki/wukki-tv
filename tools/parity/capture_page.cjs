// Capture the DOM and pixels in one browser session with an explicit content viewport.
const { chromium } = require('playwright');
const fs = require('node:fs');
(async () => {
    const [executablePath, url, screenshot, width, height] = process.argv.slice(2);
    const browser = await chromium.launch({ executablePath, headless: true });
    try {
        const page = await browser.newPage({
            viewport: { width: Number(width), height: Number(height) },
            deviceScaleFactor: 1, timezoneId: 'Europe/Budapest', locale: 'hu-HU',
        });
        await page.goto(url);
        await page.waitForSelector('html[data-parity-ready="true"]', { state: 'attached' });
        await page.evaluate(() => document.fonts.ready);
        const evidence = await page.evaluate(() => JSON.parse(document.querySelector('#wukki-parity-evidence').textContent));
        if (evidence.viewport[0] !== Number(width) || evidence.viewport[1] !== Number(height)) {
            throw new Error('DOM and screenshot viewport differ');
        }
        await page.screenshot({ path: screenshot, animations: 'disabled' });
        fs.writeFileSync(screenshot + '.json', JSON.stringify(evidence, null, 2));
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
