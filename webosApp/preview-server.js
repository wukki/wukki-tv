'use strict';

var fs = require('fs');
var http = require('http');
var https = require('https');
var path = require('path');

var host = '127.0.0.1';
var port = Number(process.env.WUKKI_WEBOS_PREVIEW_PORT || 4173);
var root = path.resolve(__dirname, 'build/dist/js/productionExecutable');
var maximumProxyBytes = 32 * 1024 * 1024;

http.createServer(function (request, response) {
    var parsed = new URL(request.url, 'http://' + host + ':' + port);
    if (parsed.pathname === '/__wukki_proxy') {
        proxy({
            url: parsed.searchParams.get('url'),
            maxBytes: parsed.searchParams.get('maxBytes')
        }, response, 0);
        return;
    }
    serveStatic(parsed.pathname, response);
}).listen(port, host, function () {
    console.log('Wukki TV webOS preview: http://' + host + ':' + port + '/');
});

function serveStatic(requestPath, response) {
    var relative = requestPath === '/' ? 'index.html' : decodeURIComponent(requestPath).replace(/^\/+/, '');
    var file = path.resolve(root, relative);
    if (file !== root && file.indexOf(root + path.sep) !== 0) {
        respond(response, 403, 'Forbidden');
        return;
    }
    fs.readFile(file, function (error, content) {
        if (error) {
            respond(response, error.code === 'ENOENT' ? 404 : 500, 'Not found');
            return;
        }
        response.writeHead(200, {
            'Content-Type': contentType(file),
            'Cache-Control': 'no-store',
            'Access-Control-Allow-Origin': '*'
        });
        response.end(content);
    });
}

function proxy(query, response, redirects) {
    var target;
    try {
        target = new URL(String(query.url || ''));
    } catch (error) {
        respond(response, 400, 'Invalid proxy URL');
        return;
    }
    if (target.protocol !== 'http:' && target.protocol !== 'https:') {
        respond(response, 400, 'Only HTTP and HTTPS URLs are supported');
        return;
    }
    var requestedMaximum = Number(query.maxBytes || maximumProxyBytes);
    var maximum = Math.min(Math.max(requestedMaximum || maximumProxyBytes, 1), maximumProxyBytes);
    var client = target.protocol === 'https:' ? https : http;
    var upstream = client.get(target, { headers: { 'Accept-Encoding': 'identity' } }, function (incoming) {
        if (incoming.statusCode >= 300 && incoming.statusCode < 400 && incoming.headers.location && redirects < 3) {
            incoming.resume();
            query.url = new URL(incoming.headers.location, target).toString();
            proxy(query, response, redirects + 1);
            return;
        }
        if (incoming.statusCode < 200 || incoming.statusCode >= 300) {
            incoming.resume();
            respond(response, incoming.statusCode || 502, 'Upstream HTTP ' + incoming.statusCode);
            return;
        }
        var declared = Number(incoming.headers['content-length']);
        if (isFinite(declared) && declared > maximum) {
            incoming.resume();
            respond(response, 413, 'Upstream response is too large');
            return;
        }
        var chunks = [];
        var size = 0;
        incoming.on('data', function (chunk) {
            size += chunk.length;
            if (size > maximum) {
                upstream.destroy();
                respond(response, 413, 'Upstream response is too large');
                return;
            }
            chunks.push(chunk);
        });
        incoming.on('end', function () {
            if (response.headersSent) return;
            response.writeHead(200, {
                'Content-Type': incoming.headers['content-type'] || 'text/plain; charset=utf-8',
                'Content-Length': size,
                'Cache-Control': 'no-store',
                'Access-Control-Allow-Origin': '*'
            });
            response.end(Buffer.concat(chunks, size));
        });
        incoming.on('error', function (error) { respond(response, 502, error.message); });
    });
    upstream.setTimeout(30000, function () { upstream.destroy(new Error('Upstream timeout')); });
    upstream.on('error', function (error) { respond(response, 502, error.message); });
}

function respond(response, status, message) {
    if (response.headersSent) return;
    response.writeHead(status, {
        'Content-Type': 'text/plain; charset=utf-8',
        'Cache-Control': 'no-store',
        'Access-Control-Allow-Origin': '*'
    });
    response.end(message);
}

function contentType(file) {
    var extension = path.extname(file).toLowerCase();
    if (extension === '.html') return 'text/html; charset=utf-8';
    if (extension === '.js') return 'application/javascript; charset=utf-8';
    if (extension === '.css') return 'text/css; charset=utf-8';
    if (extension === '.json') return 'application/json; charset=utf-8';
    if (extension === '.png') return 'image/png';
    if (extension === '.properties' || extension === '.txt') return 'text/plain; charset=utf-8';
    return 'application/octet-stream';
}
