#!/usr/bin/env node
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const repoRoot = path.resolve(projectRoot, '../../../../../');
const sourcePath = path.join(repoRoot, '03-apps/02-application/kiosk-shell/agent/src/integrations/supabase/types.ts');
const targetDir = path.join(projectRoot, 'src/integrations/supabase');
const targetPath = path.join(targetDir, 'types.generated.ts');

async function syncTypes() {
    try {
        const sourceContent = await readFile(sourcePath, 'utf8');
        const banner = `/**\n * AUTO-GENERATED FILE. DO NOT EDIT.\n * Source: ${path.relative(projectRoot, sourcePath).replace(/\\/g, '/')}\n * Synced: ${new Date().toISOString()}\n */\n`;
        await mkdir(targetDir, { recursive: true });
        await writeFile(targetPath, `${banner}${sourceContent}`, 'utf8');
        console.info(`[supabase-types] Synced to ${path.relative(projectRoot, targetPath).replace(/\\/g, '/')}`);
    } catch (error) {
        console.error('[supabase-types] Failed to sync types', error.message);
        process.exitCode = 1;
    }
}

syncTypes();
