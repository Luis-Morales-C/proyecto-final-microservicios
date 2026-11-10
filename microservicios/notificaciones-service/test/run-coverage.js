'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { fileURLToPath } = require('node:url');

const root = path.resolve(__dirname, '..');
const coverageDir = path.join(root, 'coverage');
const rawDir = path.join(coverageDir, 'raw');
fs.rmSync(coverageDir, { recursive: true, force: true });
fs.mkdirSync(rawDir, { recursive: true });

const result = spawnSync(process.execPath, ['--test', path.join(__dirname, 'notificaciones.test.js')], {
    cwd: root,
    stdio: 'inherit',
    env: { ...process.env, NODE_V8_COVERAGE: rawDir }
});

if ((result.status ?? 1) !== 0) process.exit(result.status ?? 1);

const scripts = new Map();

function walkJs(dir) {
    const out = [];
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) out.push(...walkJs(full));
        else if (entry.isFile() && entry.name.endsWith('.js')) out.push(full);
    }
    return out;
}

for (const filename of walkJs(path.join(root, 'src'))) {
    scripts.set(filename, new Map());
}
for (const name of fs.readdirSync(rawDir).filter((n) => n.endsWith('.json'))) {
    const report = JSON.parse(fs.readFileSync(path.join(rawDir, name), 'utf8'));
    for (const script of report.result || []) {
        if (!script.url.startsWith('file:')) continue;
        let filename;
        try { filename = fileURLToPath(script.url); } catch { continue; }
        const srcRoot = path.join(root, 'src') + path.sep;
        if (!filename.startsWith(srcRoot) || !filename.endsWith('.js')) continue;
        const bucket = scripts.get(filename) || new Map();
        for (const fn of script.functions || []) {
            for (const range of fn.ranges || []) {
                const key = `${range.startOffset}:${range.endOffset}`;
                const prior = bucket.get(key) || { ...range, count: 0 };
                prior.count += range.count;
                bucket.set(key, prior);
            }
        }
        scripts.set(filename, bucket);
    }
}

function isCodeLine(text) {
    const t = text.trim();
    return t !== '' && !t.startsWith('//') && !t.startsWith('/*') && !t.startsWith('*') && t !== '*/';
}

let lcov = '';
let total = 0;
let hit = 0;
const perFile = [];

for (const [filename, rangeMap] of [...scripts.entries()].sort()) {
    const source = fs.readFileSync(filename, 'utf8');
    const lines = source.split(/\r?\n/);
    const ranges = [...rangeMap.values()];
    let offset = 0;
    let lf = 0;
    let lh = 0;
    const records = [];

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const lineStart = offset;
        const lineEnd = lineStart + line.length;
        offset = lineEnd + 1;
        if (!isCodeLine(line)) continue;

        let executed = false;
        for (let pos = lineStart; pos < lineEnd; pos++) {
            if (/\s/.test(source[pos])) continue;
            const containing = ranges
                .filter((r) => r.startOffset <= pos && pos < r.endOffset)
                .sort((a, b) => (a.endOffset - a.startOffset) - (b.endOffset - b.startOffset));
            if (containing.length && containing[0].count > 0) {
                executed = true;
                break;
            }
        }
        lf++;
        if (executed) lh++;
        records.push(`DA:${i + 1},${executed ? 1 : 0}`);
    }

    const relative = path.relative(root, filename).replaceAll(path.sep, '/');
    lcov += `TN:notificaciones-service\nSF:${relative}\n${records.join('\n')}\nLF:${lf}\nLH:${lh}\nend_of_record\n`;
    total += lf;
    hit += lh;
    perFile.push({ file: relative, lines: lf, covered: lh, pct: lf ? +(lh * 100 / lf).toFixed(2) : 100 });
}

fs.writeFileSync(path.join(coverageDir, 'lcov.info'), lcov);
const summary = { lines: total, covered: hit, pct: total ? +(hit * 100 / total).toFixed(2) : 0, files: perFile };
fs.writeFileSync(path.join(coverageDir, 'summary.json'), JSON.stringify(summary, null, 2));
console.log(`\nCobertura de líneas (V8 -> LCOV): ${summary.covered}/${summary.lines} = ${summary.pct}%`);
for (const f of perFile) console.log(`  ${f.file}: ${f.covered}/${f.lines} = ${f.pct}%`);
