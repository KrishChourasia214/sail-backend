# SAIL Backend - Serverless Application Infrastructure Launcher

Full production Spring Boot backend for SAIL platform that automates AWS serverless deployments.

## 🚀 Features

- **Upload Module**: Accepts ZIP files and extracts them
- **Scan Module**: Automatically detects project type (Static Website or Spring Boot API)
- **Deploy Module**: 
  - Static websites → S3 + CloudFront
  - Spring Boot APIs → Lambda + API Gateway
- **Cost Analysis**: Real-time cost estimation
- **Deployment History**: Track all deployments

## 📋 Prerequisites

- Java 17+
- Maven 3.8+
- AWS CLI configured with credentials
- AWS SAM CLI (for Spring Boot deployments)
- AWS Account with appropriate permissions

## ⚙️ Configuration

1. **AWS Credentials**: Configure using AWS CLI or environment variables
   ```bash
   aws configure
   ```

2. **Application Properties**: Edit `src/main/resources/application.properties`
   - Set `aws.region` to your preferred region
   - Adjust temporary directory paths if needed

3. **IAM Permissions Required**:
   - S3: CreateBucket, PutObject, PutBucketWebsite, PutBucketPolicy
   - Lambda: CreateFunction, UpdateFunctionCode, InvokeFunction
   - API Gateway: CreateRestApi, CreateResource, CreateMethod, CreateIntegration
   - IAM: CreateRole, AttachRolePolicy (for Lambda execution role)

## 🏃 Running the Application

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run

# Or use the JAR
java -jar target/sail-backend-1.0.0.jar
```

The application will start on `http://localhost:8080`

## 📡 API Endpoints

### Upload
- `POST /api/upload` - Upload a ZIP file
  - Request: `multipart/form-data` with `file` parameter
  - Response: `UploadResponse` with projectId

### Scan
- `GET /api/scan/{projectId}` - Scan project structure
  - Response: `ScanResult` with project type and metadata

### Deploy
- `POST /api/deploy/static/{projectId}` - Deploy static website
- `POST /api/deploy/spring/{projectId}` - Deploy Spring Boot API
  - Response: `DeployResult` with deployment URL

### Cost
- `GET /api/cost/{projectId}` - Get cost estimation
  - Response: `CostResult` with monthly cost breakdown

### History
- `GET /api/history` - Get deployment history
  - Response: List of `HistoryEntry`

## 🗂️ Project Structure

```
sail-backend/
├── src/main/java/com/sail/
│   ├── controller/     # REST controllers
│   ├── service/         # Business logic
│   ├── aws/            # AWS SDK integrations
│   ├── utils/          # Utility classes
│   ├── dto/            # Data Transfer Objects
│   ├── model/          # JPA entities
│   ├── config/         # Configuration classes
│   └── repository/     # JPA repositories
└── src/main/resources/
    ├── application.properties
    └── sam-templates/   # SAM templates for deployments
```

## 🔧 Development

### Building
```bash
mvn clean package
```

### Testing
```bash
mvn test
```

### Database
The application uses H2 in-memory database. Access H2 Console at:
`http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/sail-db`
- Username: `sa`
- Password: (empty)

## 📝 Notes

- Temporary files are stored in `/tmp/sail/` by default
- Ensure sufficient disk space for project uploads
- AWS SAM CLI must be installed and in PATH for Spring Boot deployments
- Lambda execution role must be created manually in AWS IAM

## 🐛 Troubleshooting

1. **AWS Credentials**: Ensure AWS credentials are properly configured
2. **SAM CLI**: Verify SAM CLI is installed: `sam --version`
3. **Maven**: Ensure Maven is installed for Spring Boot builds
4. **Permissions**: Check IAM permissions for AWS services

## 📄 License

This project is part of the SAIL platform.

