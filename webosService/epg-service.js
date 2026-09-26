/* global require */
'use strict';

var Service = require('webos-service');
var http = require('http');
var https = require('https');
var urlParser = require('url');
var crypto = require('crypto');
var fs = require('fs');
var service = new Service('hu.wukki.tv.webos.epg');
// A full XMLTV document needs many bus reads on older TVs. Keep the service
// alive between them; the default webos-service idle timeout is five seconds.
if (service.activityManager) service.activityManager.idleTimeout = 60;
var documents = Object.create(null);
var downloadState = { phase: 'idle', receivedBytes: 0, totalBytes: 0 };
var MAX_BYTES = 32 * 1024 * 1024;
var MAX_CHUNK_CHARACTERS = 32 * 1024;
var RANGE_THRESHOLD_BYTES = 2 * 1024 * 1024;
var RANGE_CHUNK_BYTES = 512 * 1024;
var DOCUMENT_LIFETIME_MS = 5 * 60 * 1000;
var BUNDLED_ISRG_ROOT_X1 = loadCertificateAuthority(__dirname + '/certificates/isrg-root-x1.pem');

service.register('fetchEpg', function (message) {
    var payload = message.payload || {};
    var maximum = Math.min(positiveInteger(payload.maxBytes, MAX_BYTES), MAX_BYTES);
    var timeout = Math.min(positiveInteger(payload.timeoutMillis, 30000), 60000);
    var officialGuide = payload.url === 'https://kizman.net/guide/guide.xml';
    downloadState = { phase: 'requesting', receivedBytes: 0, totalBytes: 0 };
    function finish(error, buffer) {
        if (error) {
            downloadState = { phase: 'error', receivedBytes: downloadState.receivedBytes, totalBytes: downloadState.totalBytes, error: error.message };
            respondError(message, error);
            return;
        }
        downloadState = { phase: 'complete', receivedBytes: buffer.length, totalBytes: buffer.length };
        var token = crypto.randomBytes(16).toString('hex');
        var text = buffer.toString('utf8');
        var timer = setTimeout(function () { release(token); }, DOCUMENT_LIFETIME_MS);
        documents[token] = { text: text, timer: timer };
        message.respond({ returnValue: true, token: token, length: text.length, bytes: buffer.length });
    }
    download(payload.url, maximum, timeout, 0, false, function (error, buffer) {
        if (error && officialGuide && canUseOfficialHttpFallback(error)) {
            // This one public provider also offers HTTP. Only use it if TLS is
            // unavailable on an older TV; never downgrade arbitrary sources.
            downloadState = { phase: 'http-fallback', receivedBytes: 0, totalBytes: 0 };
            download('http://kizman.net/guide/guide.xml', maximum, timeout, 0, false, finish);
        } else {
            finish(error, buffer);
        }
    });
});

service.register('status', function (message) {
    message.respond({
        returnValue: true,
        phase: downloadState.phase,
        receivedBytes: downloadState.receivedBytes,
        totalBytes: downloadState.totalBytes,
        error: downloadState.error
    });
});

service.register('readChunk', function (message) {
    var payload = message.payload || {};
    var entry = documents[payload.token];
    if (!entry) {
        respondError(message, new Error('Az EPG letöltési munkamenet lejárt.'));
        return;
    }
    var offset = Math.max(0, positiveInteger(payload.offset, 0));
    var requested = Math.min(positiveInteger(payload.length, MAX_CHUNK_CHARACTERS), MAX_CHUNK_CHARACTERS);
    var nextOffset = Math.min(offset + requested, entry.text.length);
    message.respond({
        returnValue: true,
        chunk: entry.text.substring(offset, nextOffset),
        nextOffset: nextOffset,
        complete: nextOffset >= entry.text.length
    });
});

service.register('release', function (message) {
    release((message.payload || {}).token);
    message.respond({ returnValue: true });
});

function download(rawUrl, maximum, timeout, redirects, useBundledCertificateAuthority, callback) {
    callback = once(callback);
    var parsed;
    try {
        parsed = urlParser.parse(String(rawUrl || ''));
    } catch (error) {
        callback(error);
        return;
    }
    if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
        callback(new Error('Csak HTTP vagy HTTPS EPG-forrás engedélyezett.'));
        return;
    }
    var client = parsed.protocol === 'https:' ? https : http;
    var requestOptions = parsed;
    if (parsed.protocol === 'https:' && useBundledCertificateAuthority && BUNDLED_ISRG_ROOT_X1) {
        requestOptions = copyOptions(parsed);
        requestOptions.ca = BUNDLED_ISRG_ROOT_X1;
    }
    var delegated = false;
    var request = client.get(requestOptions, function (response) {
        if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location && redirects < 3) {
            clearTimeout(deadline);
            delegated = true;
            response.resume();
            download(urlParser.resolve(rawUrl, response.headers.location), maximum, timeout, redirects + 1, false, callback);
            return;
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
            clearTimeout(deadline);
            response.resume();
            callback(new Error('HTTP ' + response.statusCode));
            return;
        }
        var declared = Number(response.headers['content-length']);
        if (isFinite(declared) && declared > maximum) {
            clearTimeout(deadline);
            response.resume();
            callback(new Error('Az EPG túllépi a ' + maximum + ' bájtos korlátot.'));
            return;
        }
        if (declared > RANGE_THRESHOLD_BYTES && /bytes/i.test(response.headers['accept-ranges'] || '')) {
            clearTimeout(deadline);
            delegated = true;
            downloadState = { phase: 'ranged', receivedBytes: 0, totalBytes: declared };
            request.abort();
            downloadInRanges(rawUrl, declared, maximum, timeout, response.headers.etag, useBundledCertificateAuthority, callback);
            return;
        }
        var chunks = [];
        var size = 0;
        response.on('data', function (chunk) {
            size += chunk.length;
            if (size > maximum) {
                request.abort();
                callback(new Error('Az EPG túllépi a ' + maximum + ' bájtos korlátot.'));
                return;
            }
            chunks.push(chunk);
        });
        response.on('end', function () { clearTimeout(deadline); callback(null, Buffer.concat(chunks, size)); });
        response.on('error', callback);
    });
    request.setTimeout(timeout, function () {
        request.abort();
        callback(new Error('Az EPG letöltése túllépte az időkorlátot.'));
    });
    // socket.setTimeout does not cover DNS, TLS handshake or a continuously
    // trickling response. Always bound the entire request as well.
    var deadline = setTimeout(function () {
        request.abort();
        callback(new Error('Az EPG letöltése túllépte az időkorlátot.'));
    }, timeout);
    request.on('error', function (error) {
        clearTimeout(deadline);
        if (delegated) return;
        if (!useBundledCertificateAuthority && parsed.protocol === 'https:' && BUNDLED_ISRG_ROOT_X1 && isUnknownIssuer(error)) {
            download(rawUrl, maximum, timeout, redirects, true, callback);
            return;
        }
        callback(error);
    });
}

function downloadInRanges(rawUrl, total, maximum, timeout, etag, useBundledCertificateAuthority, callback) {
    var chunks = [];
    var offset = 0;
    var client = rawUrl.indexOf('https:') === 0 ? https : http;
    function next() {
        if (offset >= total) {
            callback(null, Buffer.concat(chunks, total));
            return;
        }
        var end = Math.min(offset + RANGE_CHUNK_BYTES, total) - 1;
        var options = copyOptions(urlParser.parse(rawUrl));
        options.headers = { Range: 'bytes=' + offset + '-' + end, Connection: 'close' };
        if (useBundledCertificateAuthority && BUNDLED_ISRG_ROOT_X1) options.ca = BUNDLED_ISRG_ROOT_X1;
        if (etag) options.headers['If-Range'] = etag;
        var finished = false;
        function fail(error) {
            if (finished) return;
            finished = true;
            clearTimeout(deadline);
            callback(error);
        }
        var request = client.get(options, function (response) {
            var contentRange = /^bytes (\d+)-(\d+)\/(\d+)$/.exec(response.headers['content-range'] || '');
            if (response.statusCode !== 206 || !contentRange ||
                Number(contentRange[1]) !== offset || Number(contentRange[2]) !== end || Number(contentRange[3]) !== total) {
                response.resume();
                fail(new Error('Az EPG részkérése érvénytelen választ adott.'));
                return;
            }
            var parts = [];
            var size = 0;
            response.on('data', function (part) {
                size += part.length;
                if (size > end - offset + 1 || offset + size > maximum) {
                    request.abort();
                    fail(new Error('Az EPG túllépi a ' + maximum + ' bájtos korlátot.'));
                } else {
                    parts.push(part);
                }
            });
            response.on('end', function () {
                if (finished) return;
                if (size !== end - offset + 1) {
                    fail(new Error('Az EPG részkérése hiányos adatot adott.'));
                    return;
                }
                clearTimeout(deadline);
                finished = true;
                chunks.push(Buffer.concat(parts, size));
                offset = end + 1;
                downloadState.receivedBytes = offset;
                next();
            });
            response.on('error', fail);
        });
        var deadline = setTimeout(function () {
            request.abort();
            fail(new Error('Az EPG részkérése túllépte az időkorlátot.'));
        }, timeout);
        request.on('error', fail);
    }
    next();
}

function loadCertificateAuthority(path) {
    try {
        return fs.readFileSync(path);
    } catch (error) {
        console.error('[EPG] A csomagolt ISRG Root X1 tanúsítvány nem olvasható:', error.message || error);
        return null;
    }
}

function isUnknownIssuer(error) {
    var code = error && error.code ? String(error.code) : '';
    var message = error && error.message ? String(error.message).toLowerCase() : '';
    return code === 'UNABLE_TO_GET_ISSUER_CERT_LOCALLY' ||
        code === 'UNABLE_TO_VERIFY_LEAF_SIGNATURE' ||
        message.indexOf('unable to get local issuer certificate') >= 0 ||
        message.indexOf('unable to verify the first certificate') >= 0;
}

function canUseOfficialHttpFallback(error) {
    var code = error && error.code ? String(error.code) : '';
    var message = error && error.message ? String(error.message) : '';
    return isUnknownIssuer(error) || /^(?:E(?:CONN|HOST|NET|PROTO|TIMEDOUT)|CERT_|ERR_SSL)/.test(code) ||
        /(?:időkorlátot|TLS|SSL|certificate)/i.test(message);
}

function release(token) {
    var entry = documents[token];
    if (!entry) return;
    clearTimeout(entry.timer);
    delete documents[token];
}

function copyOptions(source) {
    var result = {};
    Object.keys(source).forEach(function (key) { result[key] = source[key]; });
    return result;
}

function positiveInteger(value, fallback) {
    var parsed = Number(value);
    return isFinite(parsed) && parsed >= 0 ? Math.floor(parsed) : fallback;
}

function respondError(message, error) {
    message.respond({ returnValue: false, errorText: error && error.message ? error.message : String(error) });
}

function once(callback) {
    var called = false;
    return function () {
        if (called) return;
        called = true;
        callback.apply(null, arguments);
    };
}
