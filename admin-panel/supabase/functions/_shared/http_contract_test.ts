const source = await Deno.readTextFile(new URL('./http.ts', import.meta.url));

Deno.test('CORS requires the exact configured admin origin by default', () => {
  if (!source.includes('new URL(origin).origin === new URL(configured).origin')) {
    throw new Error('ADMIN_ORIGIN must be matched exactly');
  }
  if (!source.includes("Deno.env.get('ALLOW_LOCALHOST_ORIGIN') === 'true'")) {
    throw new Error('Localhost CORS exception must require an explicit opt-in');
  }
  if (/Access-Control-Allow-Origin['"]?\s*:\s*['"]\*['"]/.test(source)) {
    throw new Error('Wildcard CORS must not be enabled');
  }
});
