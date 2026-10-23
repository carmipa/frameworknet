/**
 * Captura screenshots full-page COM dados preenchidos e resultados exibidos.
 * Uso: node scripts/capture-screenshots.mjs [baseUrl]
 */
import { chromium } from "playwright";
import { mkdir } from "fs/promises";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BASE = (process.argv[2] || "http://localhost:8080").replace(/\/$/, "");
const OUT_DIR = path.join(__dirname, "..", "screenshots");
const ADMIN_KEY = process.env.ADMIN_API_KEY || "dev-admin-key-local";

/** @typedef {{ file: string, run: (page: import('playwright').Page) => Promise<void> }} Scenario */

/** @type {Scenario[]} */
const SCENARIOS = [
    {
        file: "00-landing",
        async run(page) {
            await page.goto(`${BASE}/`, { waitUntil: "networkidle" });
            await page.waitForSelector(".home-grid", { timeout: 15000 });
        },
    },
    {
        file: "01-home-cidr",
        async run(page) {
            await page.goto(`${BASE}/analise?tab=cidr`, { waitUntil: "networkidle" });
            await page.fill('form[data-tab-form="cidr"] input[name="ip"]', "192.168.1.10");
            await page.fill('form[data-tab-form="cidr"] input[name="cidr"]', "24");
            await submitForm(page, 'form[data-tab-form="cidr"] button[value="cidr"]');
            await page.waitForSelector(".resultado-ipv4-block", { timeout: 25000 });
            await page.waitForSelector("text=Decomposição bitwise AND", { timeout: 10000 });
        },
    },
    {
        file: "02-home-mask",
        async run(page) {
            await page.goto(`${BASE}/analise?tab=mask`, { waitUntil: "networkidle" });
            await page.fill('form[data-tab-form="mask"] input[name="mask_decimal"]', "255.255.255.240");
            await submitForm(page, 'form[data-tab-form="mask"] button[value="mask"]');
            await page.waitForSelector(".resultado-ipv4-block", { timeout: 25000 });
        },
    },
    {
        file: "03-home-wildcard",
        async run(page) {
            await page.goto(`${BASE}/analise?tab=wildcard`, { waitUntil: "networkidle" });
            await page.fill('form[data-tab-form="wildcard"] input[name="ip"]', "172.16.8.8");
            await page.fill('form[data-tab-form="wildcard"] input[name="wildcard_mask"]', "0.0.15.255");
            await submitForm(page, 'form[data-tab-form="wildcard"] button[value="wildcard"]');
            await page.waitForSelector(".resultado-ipv4-block", { timeout: 25000 });
        },
    },
    {
        file: "04-home-autoip",
        async run(page) {
            await page.goto(`${BASE}/analise?tab=autoip`, { waitUntil: "networkidle" });
            await page.fill('form[data-tab-form="autoip"] input[name="ip"]', "10.5.5.5");
            await submitForm(page, 'form[data-tab-form="autoip"] button[value="autoip"]');
            await page.waitForSelector(".resultado-ipv4-block", { timeout: 25000 });
        },
    },
    {
        file: "05-home-dominio",
        async run(page) {
            await page.goto(`${BASE}/analise?tab=dominio`, { waitUntil: "networkidle" });
            await page.fill('form[data-tab-form="dominio"] input[name="ip"]', "one.one.one.one");
            await page.fill('form[data-tab-form="dominio"] input[name="cidr"]', "24");
            await submitForm(page, 'form[data-tab-form="dominio"] button[value="dominio"]');
            await page.waitForSelector(".resultado-ipv4-block, .alert-danger", { timeout: 30000 });
        },
    },
    {
        file: "06-home-ipv6",
        async run(page) {
            await page.goto(`${BASE}/analise?tab=ipv6`, { waitUntil: "networkidle" });
            await page.fill('form[data-tab-form="ipv6"] input[name="ipv6"]', "2001:db8::1");
            await submitForm(page, 'form[data-tab-form="ipv6"] button[value="ipv6"]');
            await page.waitForSelector("text=Resultado IPv6", { timeout: 25000 });
        },
    },
    {
        file: "07-home-comparador",
        async run(page) {
            await page.goto(`${BASE}/analise?tab=comparador`, { waitUntil: "networkidle" });
            await page.fill('form[data-tab-form="comparador"] input[name="ip"]', "10.0.0.1");
            await page.fill('form[data-tab-form="comparador"] input[name="comparador_cidr_a"]', "20");
            await page.fill('form[data-tab-form="comparador"] input[name="comparador_cidr_b"]', "24");
            await submitForm(page, 'form[data-tab-form="comparador"] button[value="comparador"]');
            await page.waitForSelector("text=Comparador", { timeout: 25000 });
        },
    },
    {
        file: "08-localizacao-geo",
        async run(page) {
            await page.goto(`${BASE}/localizacao`, { waitUntil: "networkidle" });
            await page.waitForSelector("#form-geo-ip", { timeout: 15000 });
            await page.fill("#geo-ip-digitar", "8.8.8.8");
            await page.click("#btn-geo-localizar");
            await page.waitForFunction(
                () => {
                    const ok = document.querySelector("#geo-sucesso");
                    const fail = document.querySelector("#geo-falha");
                    const okVisible = ok && !ok.classList.contains("d-none");
                    const failVisible = fail && !fail.classList.contains("d-none");
                    return okVisible || failVisible;
                },
                { timeout: 30000 }
            );
            await page.waitForTimeout(1500);
        },
    },
    {
        file: "09-resolucao-calculada",
        async run(page) {
            await page.goto(`${BASE}/resolucao-problemas`, { waitUntil: "networkidle" });
            await page.fill('input[name="base_network_ip"]', "172.42.0.0/16");
            await page.fill('input[name="wan_prefix"]', "30");
            await page.fill('input[name="eigrp_as"]', "203");
            const rows = page.locator(".location-row");
            await rows.nth(0).locator('input[name="loc_name"]').fill("Matriz");
            await rows.nth(0).locator('input[name="loc_hosts"]').fill("400");
            await page.click("#btn-add-location");
            await page.waitForTimeout(400);
            await page.locator(".location-row").nth(1).locator('input[name="loc_name"]').fill("Filial I");
            await page.locator(".location-row").nth(1).locator('input[name="loc_hosts"]').fill("390");
            await submitForm(page, 'button[name="action_type"][value="calculate"]');
            await page.waitForSelector(".scenario-network-hero", { timeout: 45000 });
            await page.waitForTimeout(2500);
        },
    },
    {
        file: "10-resolucao-demo-fiap",
        async run(page) {
            await page.goto(`${BASE}/resolucao-problemas?demo=fiap`, { waitUntil: "networkidle" });
            await submitForm(page, 'button[name="action_type"][value="calculate"]');
            await page.waitForSelector(".scenario-network-hero", { timeout: 45000 });
            await page.waitForTimeout(2500);
        },
    },
    {
        file: "11-resolucao-demo-gs",
        async run(page) {
            await page.goto(`${BASE}/resolucao-problemas?demo=gs`, { waitUntil: "networkidle" });
            await submitForm(page, 'button[name="action_type"][value="calculate"]');
            await page.waitForSelector(".scenario-network-hero", { timeout: 45000 });
            await page.waitForTimeout(2500);
        },
    },
    {
        file: "12-protocolos-filtro",
        async run(page) {
            await page.goto(`${BASE}/protocolos`, { waitUntil: "networkidle" });
            await page.fill('[data-grid-search="protocolos"]', "BGP");
            await page.waitForTimeout(1000);
            await page.click('[data-grid-details="protocolos"]');
            await page.waitForSelector("#commDetailsModal.show, #commDetailsModal[style*='display: block']", {
                timeout: 8000,
            }).catch(() => page.waitForTimeout(800));
        },
    },
    {
        file: "13-portas-filtro",
        async run(page) {
            await page.goto(`${BASE}/portas`, { waitUntil: "networkidle" });
            await page.fill('[data-grid-search="portas"]', "443");
            await page.waitForTimeout(1000);
        },
    },
    {
        file: "14-documentacao",
        async run(page) {
            await page.goto(`${BASE}/documentacao`, { waitUntil: "networkidle" });
            await page.waitForTimeout(800);
        },
    },
    {
        file: "15-informacoes-geo",
        async run(page) {
            await page.goto(`${BASE}/informacoes?ip=8.8.8.8`, { waitUntil: "networkidle" });
            await page.waitForSelector("text=Consultado", { timeout: 15000 });
            await page.waitForTimeout(1000);
        },
    },
    {
        file: "16-admin-login",
        async run(page) {
            await page.goto(`${BASE}/admin/login?redirect=/export/json`, { waitUntil: "networkidle" });
            await page.fill('input[name="api_key"]', ADMIN_KEY);
            await page.waitForTimeout(500);
        },
    },
    {
        file: "17-telemetria",
        async run(page) {
            await page.goto(`${BASE}/telemetria`, { waitUntil: "networkidle" });
            await page.click("#btn-refresh-telemetria");
            await page.waitForTimeout(2000);
            await page.waitForSelector("#chart-modulos", { timeout: 15000 });
        },
    },
];

async function waitForServer(url, attempts = 60) {
    for (let i = 0; i < attempts; i++) {
        try {
            const res = await fetch(url);
            if (res.ok || res.status < 500) return;
        } catch (_) {
            // retry
        }
        await new Promise((r) => setTimeout(r, 2000));
    }
    throw new Error(`Servidor não respondeu em ${url}`);
}

async function submitForm(page, buttonSelector) {
    await ensureCsrfOnForms(page);
    await Promise.all([
        page.waitForNavigation({ waitUntil: "networkidle", timeout: 45000 }).catch(() => null),
        page.click(buttonSelector),
    ]);
    await page.waitForLoadState("networkidle").catch(() => null);
}

async function ensureCsrfOnForms(page) {
    await page.evaluate(() => {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
        const token = match ? decodeURIComponent(match[1]) : "";
        if (!token) return;
        document.querySelectorAll('form[method="POST"], form[method="post"]').forEach((form) => {
            let input = form.querySelector('input[name="csrf_token"]');
            if (!input) {
                input = document.createElement("input");
                input.type = "hidden";
                input.name = "csrf_token";
                form.appendChild(input);
            }
            input.value = token;
        });
    });
}

async function capture(page, scenario) {
    const outfile = path.join(OUT_DIR, `${scenario.file}.png`);
    await page.screenshot({ path: outfile, fullPage: true });
    console.log(`OK  ${scenario.file} -> ${outfile}`);
}

async function main() {
    console.log(`Aguardando ${BASE} ...`);
    await waitForServer(BASE);
    await mkdir(OUT_DIR, { recursive: true });

    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext({
        viewport: { width: 1440, height: 900 },
        deviceScaleFactor: 1,
    });

    await context.addCookies([
        {
            name: "ADMIN_API_KEY",
            value: ADMIN_KEY,
            domain: "localhost",
            path: "/",
            httpOnly: true,
            sameSite: "Strict",
        },
    ]);

    const page = await context.newPage();
    await page.goto(BASE, { waitUntil: "networkidle" });

    let ok = 0;
    let fail = 0;

    for (const scenario of SCENARIOS) {
        try {
            await scenario.run(page);
            await capture(page, scenario);
            ok++;
        } catch (err) {
            console.error(`ERR ${scenario.file}: ${err.message}`);
            try {
                await capture(page, { file: `${scenario.file}-erro` });
            } catch (_) {
                // ignore secondary failure
            }
            fail++;
        }
    }

    await browser.close();
    console.log(`\nConcluído: ${ok} capturas OK, ${fail} com falha. Pasta: ${OUT_DIR}`);
    process.exit(fail > 0 ? 1 : 0);
}

main().catch((err) => {
    console.error(err);
    process.exit(1);
});
