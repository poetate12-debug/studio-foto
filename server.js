import express from "express";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import dotenv from "dotenv";
import path from "node:path";
import { fileURLToPath } from "node:url";

dotenv.config();
const app = express();
const PORT = Number(process.env.PORT || 3000);
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const FAL_KEY = process.env.FAL_KEY;
const allowedModels = new Set(
  (process.env.FAL_ALLOWED_MODELS || "")
    .split(",").map((v) => v.trim()).filter(Boolean)
);
const allowedOrigins = new Set([
  "capacitor://localhost",
  "https://localhost",
  "http://localhost",
  ...(process.env.REANG_ALLOWED_ORIGINS || "")
    .split(",").map((v) => v.trim()).filter(Boolean)
]);

app.disable("x-powered-by");
app.use(helmet({ contentSecurityPolicy: false }));
app.use((req, res, next) => {
  const origin = req.get("Origin");
  if (origin && allowedOrigins.has(origin)) {
    res.setHeader("Access-Control-Allow-Origin", origin);
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Fal-Api-Key");
  }
  if (req.method === "OPTIONS") {
    if (origin && !allowedOrigins.has(origin)) return res.status(403).end();
    return res.status(204).end();
  }
  next();
});
app.use(express.json({ limit: "15mb" }));
app.use("/api/", rateLimit({ windowMs: 60_000, limit: 30, standardHeaders: true, legacyHeaders: false }));

app.get("/api/health", (_req, res) => {
  res.json({
    ok: true,
    service: "reang-studio-foto-api",
    falConfigured: Boolean(FAL_KEY),
    configuredModelCount: allowedModels.size
  });
});

// No inference job is created by this route. It asks fal's queue API for the
// status of a deliberately nonexistent request to verify authentication.
app.post("/api/fal/connection-test", async (req, res) => {
  const apiKey = typeof req.body?.apiKey === "string" ? req.body.apiKey.trim() : "";
  if (apiKey.length < 12 || apiKey.length > 512 || /[\r\n]/.test(apiKey)) {
    return res.status(400).json({ ok: false, code: "MISSING_KEY", message: "Masukkan API key fal.ai untuk sesi ini." });
  }
  try {
    const upstream = await fetch(
      "https://queue.fal.run/fal-ai/flux/schnell/requests/reang-studio-connection-check-no-job/status",
      {
        method: "GET",
        headers: { Authorization: `Key ${apiKey}` },
        signal: AbortSignal.timeout(12000)
      }
    );
    if (upstream.status === 401) {
      return res.status(401).json({ ok: false, code: "INVALID_KEY", message: "fal.ai menolak API key (401). Buat key baru dan pastikan tidak ada karakter yang terpotong." });
    }
    if (upstream.status === 403) {
      return res.status(403).json({ ok: false, code: "FORBIDDEN", message: "fal.ai menerima koneksi tetapi menolak akses untuk endpoint pemeriksaan (403). Periksa izin key atau hubungi dukungan fal.ai." });
    }
    if (upstream.status === 429) {
      return res.status(429).json({ ok: false, code: "RATE_LIMITED", message: "fal.ai membatasi permintaan sementara (429). Coba lagi beberapa saat." });
    }
    if (upstream.status === 404 || upstream.ok) {
      return res.json({ ok: true, keyValid: true, message: "Koneksi ke fal.ai berhasil dan API key diterima. Pemeriksaan ini tidak membuat job AI berbayar." });
    }
    return res.status(502).json({
      ok: false,
      code: "UPSTREAM_ERROR",
      message: `fal.ai merespons HTTP ${upstream.status}. Belum bisa memastikan key valid.`
    });
  } catch {
    return res.status(502).json({ ok: false, code: "UPSTREAM_UNREACHABLE", message: "Backend tidak dapat menghubungi fal.ai. Periksa koneksi server." });
  }
});

function requireFal(req, res, next) {
  const requestKey = req.get("X-Fal-Api-Key");
  const key = requestKey || FAL_KEY;
  if (!key) return res.status(503).json({ error: "fal.ai belum dikonfigurasi. Masukkan key pada sesi aplikasi atau atur FAL_KEY di server." });
  req.falKey = key;
  next();
}
function validateModel(modelId) {
  return typeof modelId === "string" && allowedModels.has(modelId);
}

// Model ID must be explicitly allow-listed by the server operator.
// Keys are used in-memory for the request only and are never persisted here.
app.post("/api/fal/submit", requireFal, async (req, res) => {
  const { modelId, input } = req.body || {};
  if (!validateModel(modelId)) {
    return res.status(400).json({ error: "Model tidak diizinkan. Periksa daftar FAL_ALLOWED_MODELS." });
  }
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    return res.status(400).json({ error: "Input model harus berupa objek JSON." });
  }
  try {
    const upstream = await fetch(`https://queue.fal.run/${modelId}`, {
      method: "POST",
      headers: {
        Authorization: `Key ${req.falKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(input),
      signal: AbortSignal.timeout(30000)
    });
    const body = await upstream.json().catch(() => ({}));
    res.status(upstream.status).json(body);
  } catch {
    res.status(502).json({ error: "Tidak dapat menghubungi layanan fal.ai." });
  }
});

app.get("/api/fal/status", requireFal, async (req, res) => {
  const { modelId, requestId } = req.query;
  if (!validateModel(modelId)) return res.status(400).json({ error: "Model tidak diizinkan." });
  if (typeof requestId !== "string" || !/^[a-zA-Z0-9_-]{1,200}$/.test(requestId)) {
    return res.status(400).json({ error: "requestId tidak valid." });
  }
  try {
    const upstream = await fetch(
      `https://queue.fal.run/${modelId}/requests/${encodeURIComponent(requestId)}/status`,
      { headers: { Authorization: `Key ${req.falKey}` }, signal: AbortSignal.timeout(12000) }
    );
    const body = await upstream.json().catch(() => ({}));
    res.status(upstream.status).json(body);
  } catch {
    res.status(502).json({ error: "Tidak dapat memeriksa status fal.ai." });
  }
});

app.get("/api/fal/result", requireFal, async (req, res) => {
  const { modelId, requestId } = req.query;
  if (!validateModel(modelId)) return res.status(400).json({ error: "Model tidak diizinkan." });
  if (typeof requestId !== "string" || !/^[a-zA-Z0-9_-]{1,200}$/.test(requestId)) {
    return res.status(400).json({ error: "requestId tidak valid." });
  }
  try {
    const upstream = await fetch(
      `https://queue.fal.run/${modelId}/requests/${encodeURIComponent(requestId)}`,
      { headers: { Authorization: `Key ${req.falKey}` }, signal: AbortSignal.timeout(12000) }
    );
    const body = await upstream.json().catch(() => ({}));
    res.status(upstream.status).json(body);
  } catch {
    res.status(502).json({ error: "Tidak dapat mengambil hasil fal.ai." });
  }
});

// Serve both the PWA and API from the same Render service.
app.use(express.static(__dirname));
app.get("*", (req, res, next) => {
  if (req.path.startsWith("/api/")) return next();
  res.sendFile(path.join(__dirname, "index.html"));
});

app.listen(PORT, () => {
  console.log(`Reang Studio Foto listening on port ${PORT}`);
});
