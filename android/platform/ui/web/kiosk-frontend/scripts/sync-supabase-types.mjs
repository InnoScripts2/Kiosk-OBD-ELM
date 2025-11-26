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

/**
 * Normalize Windows-style paths to POSIX form for logging.
 * @param {string} value
 * @returns {string}
 */
const normalizePath = (value) => value.replace(/\\/g, '/');

/**
 * Copy generated Supabase database types from the agent project into the kiosk frontend.
 * @returns {Promise<void>}
 */
async function syncTypes() {
    try {
        const sourceContent = await readFile(sourcePath, 'utf8');
        const banner = `/**\n * AUTO-GENERATED FILE. DO NOT EDIT.\n * Source: ${normalizePath(path.relative(projectRoot, sourcePath))}\n * Synced: ${new Date().toISOString()}\n */\n`;
        await mkdir(targetDir, { recursive: true });
        await writeFile(targetPath, `${banner}${sourceContent}`, 'utf8');
        console.info(`[supabase-types] Synced to ${normalizePath(path.relative(projectRoot, targetPath))}`);
    } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        console.error('[supabase-types] Failed to sync types', message);
        process.exitCode = 1;
    }
}

syncTypes();
