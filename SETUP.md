# SAIL Backend Setup Guide

## Prerequisites Installation

### 1. Java 17
```bash
# Check Java version
java -version

# If not installed, download from:
# https://adoptium.net/
```

### 2. Maven
```bash
# Check Maven version
mvn -version

# If not installed:
# Windows: Download from https://maven.apache.org/download.cgi
# Mac: brew install maven
# Linux: sudo apt-get install maven
```

### 3. AWS CLI
```bash
# Install AWS CLI
# Windows: Download MSI from https://aws.amazon.com/cli/
# Mac: brew install awscli
# Linux: pip install awscli

# Configure AWS credentials
aws configure
# Enter your AWS Access Key ID
# Enter your AWS Secret Access Key
# Enter default region (e.g., us-east-1)
# Enter default output format (json)
```

### 4. AWS SAM CLI
```bash
# Install SAM CLI
# Windows: Download from https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html
# Mac: brew install aws-sam-cli
# Linux: Follow AWS documentation

# Verify installation
sam --version
```

## AWS IAM Setup

### 1. Create Lambda Execution Role

1. Go to AWS IAM Console
2. Create a new role:
   - Trusted entity: AWS Lambda
   - Permissions: Attach `AWSLambdaBasicExecutionRole`
   - Name: `sail-lambda-execution-role`
3. Copy the Role ARN (e.g., `arn:aws:iam::123456789012:role/sail-lambda-execution-role`)

### 2. Update application.properties

Add the Lambda execution role ARN:
```properties
aws.lambda.execution.role=arn:aws:iam::YOUR_ACCOUNT_ID:role/sail-lambda-execution-role
```

### 3. User Permissions

Your AWS user/role needs these permissions:
- S3: Full access (or specific: CreateBucket, PutObject, PutBucketWebsite, PutBucketPolicy)
- Lambda: Full access (or specific: CreateFunction, UpdateFunctionCode, InvokeFunction)
- API Gateway: Full access (or specific: CreateRestApi, CreateResource, CreateMethod, CreateIntegration)
- IAM: PassRole (to allow Lambda to assume the execution role)

## Running the Application

### 1. Build the Project
```bash
cd sail-backend
mvn clean install
```

### 2. Configure Application
Edit `src/main/resources/application.properties`:
- Set `aws.region` to your preferred region
- Set `aws.lambda.execution.role` to your Lambda execution role ARN
- Adjust temporary directory paths if needed (Windows users may need to change `/tmp` paths)

### 3. Run the Application
```bash
mvn spring-boot:run
```

Or build and run the JAR:
```bash
mvn clean package
java -jar target/sail-backend-1.0.0.jar
```

### 4. Verify
- Application should start on `http://localhost:8080`
- Check H2 console: `http://localhost:8080/h2-console`
- Test upload endpoint: `POST http://localhost:8080/api/upload`

## Frontend Integration

### Update Frontend API Base URL

In your React frontend, update API calls to point to:
```javascript
const API_BASE_URL = 'http://localhost:8080/api';
```

### Example Frontend Integration

Update your frontend upload component:
```javascript
const handleUpload = async () => {
  const formData = new FormData();
  formData.append('file', file);
  
  const response = await fetch('http://localhost:8080/api/upload', {
    method: 'POST',
    body: formData
  });
  
  const data = await response.json();
  // Use data.projectId for subsequent API calls
};
```

## Testing the Deployment Flow

### 1. Upload a Static Website
- Create a ZIP file with `index.html` and assets
- Upload via `POST /api/upload`
- Get `projectId` from response

### 2. Scan the Project
- Call `GET /api/scan/{projectId}`
- Should return `projectType: "STATIC"`

### 3. Deploy Static Website
- Call `POST /api/deploy/static/{projectId}`
- Should return S3 website URL

### 4. Upload a Spring Boot Project
- Create a ZIP file with `pom.xml` and Spring Boot structure
- Upload via `POST /api/upload`
- Scan and deploy via `POST /api/deploy/spring/{projectId}`

## Troubleshooting

### Issue: AWS Credentials Not Found
**Solution**: Run `aws configure` and enter your credentials

### Issue: SAM CLI Not Found
**Solution**: Install SAM CLI and ensure it's in your PATH

### Issue: Lambda Execution Role Error
**Solution**: Create the IAM role and update `application.properties`

### Issue: Permission Denied on /tmp
**Solution**: On Windows, change temp directories in `application.properties`:
```properties
sail.temp.upload.dir=C:/tmp/sail/uploads
sail.temp.extracted.dir=C:/tmp/sail/extracted
sail.temp.build.dir=C:/tmp/sail/build
```

### Issue: Maven Build Fails
**Solution**: Ensure Maven is installed and `mvn` is in PATH

## Next Steps

1. Test with a simple static website
2. Test with a Spring Boot application
3. Integrate with your frontend
4. Deploy to production (consider using AWS Elastic Beanstalk or EC2)

