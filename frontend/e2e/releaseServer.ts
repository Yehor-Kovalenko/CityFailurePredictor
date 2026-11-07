import { createServer, type Server } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import type { AddressInfo } from "node:net";

const DIST = join(import.meta.dirname, "..", "dist");

const MIME: Record<string, string> = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
  ".webmanifest": "application/manifest+json",
};

export interface ReleaseServer {
  url: string;
  deployNewRelease(): void;
  close(): Promise<void>;
}

export async function startReleaseServer(): Promise<ReleaseServer> {
  let release = 1;

  const server: Server = createServer(async (request, response) => {
    const path = normalize(decodeURIComponent(new URL(request.url ?? "/", "http://x").pathname));
    let file = join(DIST, path);
    if (!file.startsWith(DIST)) {
      response.writeHead(403).end();
      return;
    }
    let body: Buffer;
    try {
      body = await readFile(file);
    } catch {
      file = join(DIST, "index.html");
      body = await readFile(file);
    }
    if (path === "/sw.js") {
      body = Buffer.concat([body, Buffer.from(`\n// release ${release}\n`)]);
    }
    response.writeHead(200, {
      "Content-Type": MIME[extname(file)] ?? "application/octet-stream",
      "Cache-Control": "no-cache",
    });
    response.end(body);
  });

  await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address() as AddressInfo;

  return {
    url: `http://127.0.0.1:${port}`,
    deployNewRelease: () => {
      release++;
    },
    close: () => new Promise((resolve, reject) => server.close((error) => (error ? reject(error) : resolve()))),
  };
}
