config.module.rules.push({
    test: /\.js$/,
    exclude: /node_modules/,
    use: {
        loader: "babel-loader",
        options: {
            presets: [
                ["@babel/preset-env", {
                    targets: { chrome: "68" },
                    bugfixes: true,
                    modules: false
                }]
            ]
        }
    }
});

// Terser may reintroduce nullish coalescing after Babel has transpiled the
// modules. Chromium 68 cannot parse that syntax, so keep the TV bundle
// unminified until the minifier can be pinned to the same browser target.
config.optimization = config.optimization || {};
config.optimization.minimize = false;
