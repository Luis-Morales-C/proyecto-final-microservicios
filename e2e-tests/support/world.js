import {
    setWorldConstructor,
    setDefaultTimeout
} from "@cucumber/cucumber";
import { existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { loadEnvFile } from "node:process";

setDefaultTimeout(30000);

const here = dirname(fileURLToPath(import.meta.url));
const rootEnv = resolve(here, "../../.env");

// Node 22 permite cargar el .env sin agregar otra dependencia.
if (existsSync(rootEnv)) {
    loadEnvFile(rootEnv);
}

class CustomWorld {
    constructor() {
        this.baseUrl = process.env.BASE_URL ?? "http://localhost:8080";
        this.adminEmail = process.env.ADMIN_EMAIL ?? "admin@empresa.com";
        this.adminPassword = process.env.ADMIN_PASSWORD ?? "Admin12345";

        this.token = null;
        this.adminToken = null;
        this.response = null;
        this.responseBody = null;
        this.empleadoId = null;
        this.email = null;
        this.password = null;
        this.vacacionId = null;
        this.departamentoId = null;
        this.testData = {};
    }
}

setWorldConstructor(CustomWorld);
