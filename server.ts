import http from 'http';
import path from 'path';
import { fileURLToPath } from 'url';
import express from 'express';
import { createBackendApp } from './src/backend/app';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

async function startServer() {
  const app = createBackendApp();
  const PORT = parseInt(process.env.DEFAULT_APP_PORT || '3000', 10);
  const isProd = process.env.NODE_ENV === 'production';
  const httpServer = http.createServer(app);

  if (!isProd) {
    // Development mode: Mount Vite middleware
    // Share the HTTP server so HMR upgrades the primary port rather than binding
    // to fixed auxiliary port 24678, which conflicts during rapid restarts.
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: {
        middlewareMode: true,
        hmr: {
          server: httpServer,
        },
      },
      appType: 'spa',
    });
    app.use(vite.middlewares);
    console.log('[Server] Vite middleware mounted in dev mode');
  } else {
    // Production mode: Serve built static assets
    const distPath = path.resolve(__dirname, 'dist');
    app.use(express.static(distPath));
    app.get('*', (_req, res) => {
      res.sendFile(path.resolve(distPath, 'index.html'));
    });
    console.log('[Server] Serving production build from dist');
  }

  httpServer.listen(PORT, '0.0.0.0', () => {
    console.log(`[Mahi Repair Cloud Backend] Server listening on http://0.0.0.0:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('[Server] Failed to start:', err);
  process.exit(1);
});
