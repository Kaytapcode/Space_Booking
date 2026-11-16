#!/usr/bin/env node
// PreToolUse guard for Edit|Write|MultiEdit: blocks writes to the frozen contract and to real .env files.
const fs = require('fs');
const path = require('path');

function block(msg) {
  process.stderr.write(msg + '\n');
  process.exit(2);
}

let raw = '';
process.stdin.on('data', (c) => (raw += c));
process.stdin.on('end', () => {
  let input;
  try {
    input = JSON.parse(raw);
  } catch {
    block('guard.js: could not parse hook input; refusing the edit.');
  }
  const file = input && input.tool_input && input.tool_input.file_path;
  if (!file) process.exit(0);

  const root = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
  const rel = path.relative(root, path.resolve(root, file)).split(path.sep).join('/').toLowerCase();
  const base = path.basename(rel);

  if (rel.startsWith('docs/contract/') && fs.existsSync(path.join(root, 'docs', 'contract', '.frozen'))) {
    block('docs/contract/ is frozen. Get explicit user approval, then follow the change protocol in .claude/rules/contract.md.');
  }
  if ((base === '.env' || base.startsWith('.env.')) && base !== '.env.example') {
    block('Editing ' + base + ' is blocked. Only .env.example may be written.');
  }
  process.exit(0);
});
