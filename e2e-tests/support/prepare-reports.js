import { mkdir, rm } from "node:fs/promises";

await rm(new URL("../reports", import.meta.url), { recursive: true, force: true });
await mkdir(new URL("../reports", import.meta.url), { recursive: true });
