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
var MAX_BYTES = 32 * 1024 * 1024;
var MAX_CHUNK_CHARACTERS = 32 * 1024;
var DOCUMENT_LIFETIME_MS = 5 * 60 * 1000;
var BUNDLED_ISRG_ROOT_X1 = loadCertificateAuthority(__dirname + '/certificates/isrg-root-x1.pem');

service.register('fetchEpg', function (message) {
    var payload = message.payload || {};
    var maximum = Math.min(positiveInteger(payload.maxBytes, MAX_BYTES), MAX_BYTES);
    var timeout = Math.min(positiveInteger(payload.timeoutMillis, 30000), 60000);
    download(payload.url, maximum, timeout, 0, false, function (error, buffer) {
        if (error) {
            respondError(message, error);
            return;
        }
        var token = crypto.randomBytes(16).toString('hex');
        var text = buffer.toString('utf8');
        var timer = setTimeout(function () { release(token); }, DOCUMENT_LIFETIME_MS);
        documents[token] = { text: text, timer: timer };
        message.respond({ returnValue: true, token: token, length: text.length, bytes: buffer.length });
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
        requestOptions = Object.assign({}, parsed, { ca: BUNDLED_ISRG_ROOT_X1 });
    }
    var request = client.get(requestOptions, function (response) {
        if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location && redirects < 3) {
            response.resume();
            download(urlParser.resolve(rawUrl, response.headers.location), maximum, timeout, redirects + 1, false, callback);
            return;
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
            response.resume();
            callback(new Error('HTTP ' + response.statusCode));
            return;
        }
        var declared = Number(response.headers['content-length']);
        if (isFinite(declared) && declared > maximum) {
            response.resume();
            callback(new Error('Az EPG túllépi a ' + maximum + ' bájtos korlátot.'));
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
        response.on('end', function () { callback(null, Buffer.concat(chunks, size)); });
        response.on('error', callback);
    });
    request.setTimeout(timeout, function () {
        request.abort();
        callback(new Error('Az EPG letöltése túllépte az időkorlátot.'));
    });
    request.on('error', function (error) {
        if (!useBundledCertificateAuthority && parsed.protocol === 'https:' && BUNDLED_ISRG_ROOT_X1 && isUnknownIssuer(error)) {
            download(rawUrl, maximum, timeout, redirects, true, callback);
            return;
        }
        callback(error);
    });
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

function release(token) {
    var entry = documents[token];
    if (!entry) return;
    clearTimeout(entry.timer);
    delete documents[token];
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
