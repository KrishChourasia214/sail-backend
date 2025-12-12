# SAIL Backend API Reference

## Base URL
```
http://localhost:8080/api
```

## Endpoints

### 1. Upload Project
**POST** `/api/upload`

Upload a ZIP file containing your project.

**Request:**
- Content-Type: `multipart/form-data`
- Body: `file` (ZIP file)

**Response:**
```json
{
  "projectId": "550e8400-e29b-41d4-a716-446655440000",
  "fileName": "my-project.zip",
  "sizeMB": 1.21,
  "status": "RECEIVED"
}
```

**Example (cURL):**
```bash
curl -X POST http://localhost:8080/api/upload \
  -F "file=@my-project.zip"
```

---

### 2. Scan Project
**GET** `/api/scan/{projectId}`

Scan the uploaded project to detect its type and extract metadata.

**Response (Static Website):**
```json
{
  "projectType": "STATIC",
  "entryFile": "index.html",
  "htmlFiles": 3,
  "jsFiles": 1,
  "cssFiles": 2,
  "rootFolder": "/tmp/sail/extracted/550e8400-e29b-41d4-a716-446655440000"
}
```

**Response (Spring Boot):**
```json
{
  "projectType": "SPRINGBOOT",
  "mainClass": "com.example.taskmanager.TaskmanagerApplication",
  "endpoints": ["/tasks", "/tasks/{id}"],
  "rootFolder": "/tmp/sail/extracted/550e8400-e29b-41d4-a716-446655440000"
}
```

**Example:**
```bash
curl http://localhost:8080/api/scan/550e8400-e29b-41d4-a716-446655440000
```

---

### 3. Deploy Static Website
**POST** `/api/deploy/static/{projectId}`

Deploy a static website to AWS S3.

**Response:**
```json
{
  "deploymentType": "STATIC",
  "url": "http://sail-deployment-abc123.s3-website-us-east-1.amazonaws.com",
  "bucket": "sail-deployment-abc123",
  "region": "us-east-1",
  "status": "SUCCESS"
}
```

**Example:**
```bash
curl -X POST http://localhost:8080/api/deploy/static/550e8400-e29b-41d4-a716-446655440000
```

---

### 4. Deploy Spring Boot API
**POST** `/api/deploy/spring/{projectId}`

Deploy a Spring Boot application to AWS Lambda + API Gateway.

**Response:**
```json
{
  "deploymentType": "SPRINGBOOT",
  "lambdaName": "sail-function-1704067200000",
  "apiUrl": "https://abc123.execute-api.us-east-1.amazonaws.com/",
  "region": "us-east-1",
  "status": "SUCCESS"
}
```

**Example:**
```bash
curl -X POST http://localhost:8080/api/deploy/spring/550e8400-e29b-41d4-a716-446655440000
```

---

### 5. Get Cost Estimation
**GET** `/api/cost/{projectId}`

Get monthly cost estimation for the deployment.

**Response:**
```json
{
  "projectType": "SPRINGBOOT",
  "lambdaCost": 0.21,
  "apiGatewayCost": 0.04,
  "s3Cost": 0.01,
  "total": 0.26,
  "currency": "USD",
  "period": "monthly"
}
```

**Example:**
```bash
curl http://localhost:8080/api/cost/550e8400-e29b-41d4-a716-446655440000
```

---

### 6. Get Deployment History
**GET** `/api/history`

Get all past deployments.

**Response:**
```json
[
  {
    "projectId": "550e8400-e29b-41d4-a716-446655440000",
    "fileName": "my-project.zip",
    "deploymentType": "STATIC",
    "url": "http://sail-deployment-abc123.s3-website-us-east-1.amazonaws.com",
    "status": "SUCCESS",
    "timestamp": "2025-01-12T10:30:00",
    "region": "us-east-1"
  }
]
```

**Example:**
```bash
curl http://localhost:8080/api/history
```

---

## Error Responses

All endpoints may return error responses in the following format:

**Status Code:** `500 Internal Server Error`

**Response:**
```json
{
  "status": "FAILED",
  "errorMessage": "Detailed error message"
}
```

## Frontend Integration Flow

1. **Upload** → User uploads ZIP file
   - Frontend: `POST /api/upload`
   - Store `projectId` from response

2. **Scan** → System detects project type
   - Frontend: `GET /api/scan/{projectId}`
   - Display project type and metadata

3. **Deploy** → Deploy based on project type
   - Static: `POST /api/deploy/static/{projectId}`
   - Spring Boot: `POST /api/deploy/spring/{projectId}`
   - Display deployment URL

4. **Cost** → Show cost estimation
   - Frontend: `GET /api/cost/{projectId}`
   - Display cost breakdown in charts

5. **History** → Show deployment history
   - Frontend: `GET /api/history`
   - Display in history table

## CORS Configuration

The backend is configured to accept requests from:
- `http://localhost:5173` (Vite default)
- `http://localhost:3000` (Create React App default)

To add more origins, update `AppConfig.java`.

