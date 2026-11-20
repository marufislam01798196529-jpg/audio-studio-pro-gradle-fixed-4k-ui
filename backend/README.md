# X-THREX Remote Cloud Build & Container Backend

Standalone, independently deployable backend providing:
- Isolated Android Gradle APK / AAB compilation (`POST /api/builds`, `GET /api/builds/:jobId`, `GET /api/builds/:jobId/artifact`)
- Sandboxed terminal execution with strict command allowlist & path traversal protection (`POST /api/terminal/exec`)
- Cloud task queue (`POST /api/tasks/enqueue`)
- Automatic temporary workspace cleanup (2-hour TTL)

## Deploy with Docker

```bash
cd backend
docker build -t xthrex-backend .
docker run -d -p 8080:8080 -e XTHREX_BACKEND_TOKEN="your-strong-secret" xthrex-backend
```

Then enter your backend URL and token in the X-THREX Android app under **Settings -> Cloud Build & Remote Container Backend**.
