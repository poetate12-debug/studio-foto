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

app.disable("x-powered-by");
app.use(helmet({ contentSecurityPolicy: false }));
app.use(express.json({ limit: "15mb" }));
app.use("/api/", rateLimit({ windowMs: 60_000, limit: 30, standardHeaders: true, legacyHeaders: false }));

app.get("/api/health", (_req, res) => {
  res.json({
    ok: true,
    falConfigured: Boolean(FAL_KEY),
    configuredModelCount: allowedModels.size
  });
});

function requireFal(req, res, next) {
  if (!FAL_KEY) return res.status(503).json({ error: "Fal.ai belum dikonfigurasi di server." });
  next();
}
function validateModel(modelId) {
  return typeof modelId === "string" && allowedModels.has(modelId);
}

// Model ID must be explicitly allow-listed by the server operator.
// The client never supplies a URL and never receives the secret key.
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
        Authorization: `Key ${FAL_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(input)
    });
    const body = await upstream.json().catch(() => ({}));
    res.status(upstream.status).json(body);
  } catch {
    res.status(502).json({ error: "Tidak dapat menghubungi layanan fal.ai." });
  }
});

app.get("/api/fal/status", requireFal, async (req, res) => {
  const { modelId, requestId } = req.query;
  if (!validateModel(modelId)) {
    return res.status(400).json({ error: "Model tidak diizinkan." });
  }
  if (typeof requestId !== "string" || !/^[a-zA-Z0-9_-]{1,200}$/.test(requestId)) {
    return res.status(400).json({ error: "requestId tidak valid." });
  }
  try {
    const upstream = await fetch(
      `https://queue.fal.run/${modelId}/requests/${encodeURIComponent(requestId)}/status`,
      { headers: { Authorization: `Key ${FAL_KEY}` } }
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
      { headers: { Authorization: `Key ${FAL_KEY}` } }
    );
    const body = await upstream.json().catch(() => ({}));
    res.status(upstream.status).json(body);
  } catch {
    res.status(502).json({ error: "Tidak dapat mengambil hasil fal.ai." });
  }
});

// Serve the existing PWA from the repository root when deployed as a Node app.
app.use(express.static(__dirname));
app.get("*", (req, res, next) => {
  if (req.path.startsWith("/api/")) return next();
  res.sendFile(path.join(__dirname, "index.html"));
});

app.listen(PORT, () => {
  console.log(`Reang Studio Foto listening on port ${PORT}`);
});
