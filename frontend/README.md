# CAP-39 Manufacturing Intelligence Console

Responsive React 19 + Vite frontend for the CAP-39 Manufacturing Yield Loss & Defect Genealogy Analyzer.

## Included workspaces

- Overview: batch, defect, ETL acceptance/rejection and production KPIs.
- Data ingestion: upload CSV files for the 13 supported datasets and inspect upload history.
- Pipeline runs: start asynchronous processing, poll the persistent pipeline status, inspect step state and retry eligible failures.
- Analytics: consumes all eight Spring Boot analytics endpoints and renders yield-loss, defect, production, root-cause candidate, machine, process-parameter, rework and supplier-quality information.
- Batch and defect registries: reads the existing application registry endpoints.
- Genealogy: displays parent-child batch relationships and material traceability, with optional batch filtering.
- Connections: displays Snowflake health and runs the S3 connection test.

## Run locally

```bash
npm install
cp .env.example .env
npm run dev
```

Open http://localhost:5173. The Vite development server proxies `/api` to `http://localhost:8080` by default.

## Environment

```env
VITE_API_BASE_URL=
VITE_API_TIMEOUT_MS=30000
VITE_PIPELINE_POLL_MS=3000
```

Leave `VITE_API_BASE_URL` blank for the local Vite proxy. For a separately hosted frontend, set it to the backend origin, configure backend CORS for the frontend origin, and use HTTPS.

## Production build

```bash
npm run build
npm run preview
```

The production build is generated in `dist/`. Serve that directory with Nginx or another static web server. Do not put AWS credentials, Snowflake credentials, or other secrets in frontend environment variables.

## Backend API integration

### Platform and ingestion
- `GET /api/health/snowflake`
- `POST /api/health/s3`
- `GET /api/uploads`
- `POST /api/uploads` (multipart fields: `file`, `uploadedBy`)
- `GET /api/uploads/{uploadId}`
- `GET /api/batches`
- `GET /api/defects`

### Asynchronous pipeline
- `POST /api/uploads/{uploadId}/pipeline`
- `GET /api/uploads/{uploadId}/pipeline`
- `POST /api/uploads/{uploadId}/pipeline/retry`

### Analytics
- `GET /api/analytics/overview`
- `GET /api/analytics/yield-loss`
- `GET /api/analytics/defects`
- `GET /api/analytics/production`
- `GET /api/analytics/genealogy`
- `GET /api/analytics/root-causes`
- `GET /api/analytics/rework`
- `GET /api/analytics/suppliers`

The analytics screens are based on the actual view column inventory supplied for the ten `SRIRAM_ANALYTICS` views. Production and genealogy endpoints return objects containing multiple result arrays; the frontend handles those object responses rather than treating them as flat arrays.

## Important operational notes

- The browser never connects directly to Snowflake or S3; all access is mediated by Spring Boot.
- Pipeline state returned by the backend is authoritative. Browser polling is only for displaying progress.
- The frontend is not an authentication system. Before public deployment, put authentication and role-based authorization in front of operational actions, restrict CORS, use HTTPS, and configure secure session/token handling.
- The Python ETL status is described as validated during processing; the frontend does not claim a separate Python health endpoint exists.
- Root-cause candidates are presented as investigation leads, not proven causes.
