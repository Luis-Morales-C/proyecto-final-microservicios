'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const reports = path.join(root, 'reports');
fs.mkdirSync(reports, { recursive: true });
const output = path.join(reports, 'junit.xml');
const result = spawnSync(process.execPath, [
    '--test',
    '--test-reporter=junit',
    `--test-reporter-destination=${output}`,
    path.join(__dirname, 'notificaciones.test.js')
], { cwd: root, stdio: 'inherit', env: process.env });
process.exit(result.status ?? 1);
