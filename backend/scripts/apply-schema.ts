import "dotenv/config";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { Pool } from "pg";

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error("DATABASE_URL is required in backend/.env.");

const pool = new Pool({
  connectionString: databaseUrl,
  ssl: databaseUrl.includes("localhost") ? false : { rejectUnauthorized: true }
});

try {
  const root = resolve(import.meta.dirname, "../..");
  await pool.query(await readFile(resolve(root, "database/schema.sql"), "utf8"));
  await pool.query(await readFile(resolve(root, "database/seed.sql"), "utf8"));
  const [{ count: tables }] = (await pool.query<{ count: number }>(
    "SELECT count(*)::int AS count FROM information_schema.tables WHERE table_schema = 'public'"
  )).rows;
  const [{ count: scenarios }] = (await pool.query<{ count: number }>("SELECT count(*)::int AS count FROM scenarios")).rows;
  console.log(`Neon schema ready: ${tables} tables, ${scenarios} scenarios.`);
} finally {
  await pool.end();
}

