# Deepslate Client website

Landing page for [deepslateclient.xyz](https://deepslateclient.xyz). Next.js app, deployed on Vercel from this folder.

## Local

```bash
cd website
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

## Deploy on Vercel

1. Push this repo to GitHub.
2. Import [dragos300/deepslate-client](https://github.com/dragos300/deepslate-client) at [vercel.com/new](https://vercel.com/new).
3. Set **Root Directory** to `website`. Framework Preset: Next.js.
4. Deploy. You will get a `*.vercel.app` URL first.

The Download buttons hit `/download/windows`, `/download/mac`, and `/download/linux`, which redirect straight to the matching GitHub Release asset so the file starts downloading immediately.

## Custom domain (GoDaddy → Vercel)

Keep GoDaddy as the registrar and nameservers. Do not use GoDaddy Website Builder.

1. In Vercel: Project → **Settings** → **Domains** → add `deepslateclient.xyz` and `www.deepslateclient.xyz`.
2. In GoDaddy: **My Products** → **Domains** → `deepslateclient.xyz` → **DNS**.
3. Set these records (delete parking / forwarding / extra A records for `@` that conflict):

| Type  | Name | Value                 | TTL  |
| ----- | ---- | --------------------- | ---- |
| A     | `@`  | `76.76.21.21`         | 600  |
| CNAME | `www`| `cname.vercel-dns.com`| 600  |

4. Wait for SSL on Vercel (often minutes, sometimes a few hours).
