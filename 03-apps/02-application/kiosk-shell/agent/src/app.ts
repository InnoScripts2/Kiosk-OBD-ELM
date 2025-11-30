import express from 'express';
import fs from 'node:fs';
import path from 'node:path';
import type { PaymentService } from './services/PaymentService.js';
import type { LockController } from './services/LockController.js';
import type { ReportService } from './services/ReportService.js';
import { createPaymentsRouter } from './routes/payments.js';
import { createLocksRouter } from './routes/locks.js';
import { createHealthRouter } from './routes/health.js';
import { createReportsRouter } from './routes/reports.js';
import type { ReportIngestService } from './services/ReportIngestService.js';

export interface AgentAppOptions {
  paymentService: PaymentService;
  lockController: LockController;
  reportService: ReportService;
  reportIngestService: ReportIngestService;
  staticUi?: StaticUiOptions;
}

export interface StaticUiOptions {
  rootDir: string;
  routePrefix?: string;
  indexFile?: string;
}

export function createAgentApp(options: AgentAppOptions): express.Application {
  const app = express();

  app.disable('x-powered-by');
  app.use(express.json({ limit: '1mb' }));

  app.get('/', (_req: express.Request, res: express.Response): void => {
    res.json({ status: 'ok' });
  });

  app.use('/health', createHealthRouter(options));
  app.use('/payments', createPaymentsRouter(options.paymentService));
  app.use('/locks', createLocksRouter(options.lockController));
  app.use('/reports', createReportsRouter(options.reportIngestService));

  if (options.staticUi) {
    mountStaticUi(app, options.staticUi);
  }

  app.use((_req: express.Request, res: express.Response): void => {
    res.status(404).json({ error: 'not_found' });
  });

  app.use((err: Error, _req: express.Request, res: express.Response, next: express.NextFunction): void => {
    console.error('[agent] Unhandled error', err);
    if (res.headersSent) {
      next(err);
      return;
    }
    res.status(500).json({ error: 'internal_error' });
  });

  return app;
}

function mountStaticUi(app: express.Application, options: StaticUiOptions): void {
  const normalizedPrefix = normalizePrefix(options.routePrefix ?? '/ui');
  const rootDir = path.resolve(options.rootDir);
  const indexFile = options.indexFile ?? 'index.html';
  const hashedAssetPattern = /-[A-Za-z0-9]{8,}\.(?:js|css|json|webmanifest)$/;

  if (!fs.existsSync(rootDir)) {
    console.warn(`[agent] Static UI directory not found: ${rootDir}`);
    return;
  }

  app.use(
    normalizedPrefix,
    express.static(rootDir, {
      fallthrough: true,
      setHeaders: (res, servedPath) => {
        const basename = path.basename(servedPath);
        if (hashedAssetPattern.test(basename)) {
          res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
        } else if (basename.endsWith('.html')) {
          res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
        } else {
          res.setHeader('Cache-Control', 'public, max-age=300');
        }
      },
    }),
  );

  app.get(`${normalizedPrefix}*`, (req: express.Request, res: express.Response, next: express.NextFunction): void => {
    const relativePath = req.path.slice(normalizedPrefix.length).replace(/^\/+/, '');
    if (relativePath && path.extname(relativePath)) {
      next();
      return;
    }

    res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
    res.sendFile(indexFile, { root: rootDir }, (error) => {
      if (error) {
        next(error);
      }
    });
  });
}

function normalizePrefix(prefix: string): string {
  if (!prefix.startsWith('/')) {
    return normalizePrefix(`/${prefix}`);
  }
  return prefix.endsWith('/') ? prefix : `${prefix}/`;
}