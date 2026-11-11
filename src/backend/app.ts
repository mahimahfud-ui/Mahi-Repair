import express, { Express, Request, Response, NextFunction } from 'express';
import cors from 'cors';
import { rateLimitMiddleware } from './security/auth';
import { authRouter } from './routes/auth';
import { membersRouter } from './routes/members';
import { devicesRouter } from './routes/devices';
import { toolsRouter } from './routes/tools';
import { recipesRouter } from './routes/recipes';
import { runsRouter } from './routes/runs';
import { analyticsRouter } from './routes/analytics';
import { adminRouter } from './routes/admin';
import { publicRouter } from './routes/public';
import { compatRouter } from './routes/compat';
import { remoteSupportRouter } from './routes/remoteSupport';

export function createBackendApp(): Express {
  const app = express();

  // Basic security and parsing middlewares
  app.use(cors({ origin: true, credentials: true }));
  app.use(express.json({ limit: '10mb' }));
  app.use(express.urlencoded({ extended: true, limit: '10mb' }));

  // Global rate limiter (120 req / minute per IP)
  app.use(rateLimitMiddleware(120, 60 * 1000));

  // Mount Supabase and edge-function compatible endpoints
  app.use(compatRouter);

  // Mount API v1 Routers
  app.use('/api/v1/auth', authRouter);
  app.use('/api/v1/members', membersRouter);
  app.use('/api/v1/devices', devicesRouter);
  app.use('/api/v1/tools', toolsRouter);
  app.use('/api/v1/recipes', recipesRouter);
  app.use('/api/v1/repair-runs', runsRouter);
  app.use('/api/v1/analytics', analyticsRouter);
  app.use('/api/v1/admin', adminRouter);
  app.use('/api/v1/remote-support', remoteSupportRouter);
  app.use('/api/v1', publicRouter);

  // Fallback 404 handler for API routes
  app.use('/api', (_req: Request, res: Response) => {
    res.status(404).json({
      error: {
        code: 'NOT_FOUND',
        message: 'The requested API endpoint does not exist.',
      },
    });
  });

  // Global Error Handler
  app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
    console.error('Unhandled server error:', err);
    const message = err instanceof Error ? err.message : 'Internal Server Error';
    res.status(500).json({
      error: {
        code: 'INTERNAL_SERVER_ERROR',
        message,
      },
    });
  });

  return app;
}
