'use strict';

const assert = require('assert');
const http = require('http');
const Module = require('module');

let service;
const originalLoad = Module._load;
Module._load = function (name) {
    if (name === 'webos-service') {
        return class FakeService {
            constructor() {
                service = this;
                this.activityManager = {};
                this.handlers = {};
            }
            register(name, handler) { this.handlers[name] = handler; }
        };
    }
    return originalLoad.apply(this, arguments);
};
require('../../webosService/epg-service.js');
Module._load = originalLoad;

function call(name, payload) {
    return new Promise(resolve => service.handlers[name]({ payload, respond: resolve }));
}

async function main() {
    const xml = '<tv>' + '<programme channel="M1">Árvíztűrő tükörfúrógép</programme>'.repeat(150000) + '</tv>';
    const server = http.createServer((_request, response) => {
        response.setHeader('Content-Type', 'text/xml; charset=utf-8');
        response.end(xml);
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    try {
        assert.strictEqual(service.activityManager.idleTimeout, 60);
        const result = await call('fetchEpg', {
            url: `http://127.0.0.1:${server.address().port}/guide.xml`,
            maxBytes: 32 * 1024 * 1024,
            timeoutMillis: 30000
        });
        assert.strictEqual(result.returnValue, true);
        assert.strictEqual(result.length, xml.length);
        let offset = 0;
        const chunks = [];
        while (true) {
            const part = await call('readChunk', { token: result.token, offset, length: 128 * 1024 });
            assert.strictEqual(part.returnValue, true);
            assert(part.chunk.length <= 32 * 1024);
            chunks.push(part.chunk);
            if (part.complete) break;
            assert(part.nextOffset > offset);
            offset = part.nextOffset;
        }
        assert.strictEqual(chunks.join(''), xml);
        assert(chunks.length > 100);
        await call('release', { token: result.token });
        assert.strictEqual((await call('readChunk', { token: result.token, offset: 0 })).returnValue, false);
        console.log(`EPG service: ${Buffer.byteLength(xml)} bytes, ${chunks.length} bounded chunks`);
    } finally {
        server.close();
    }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
