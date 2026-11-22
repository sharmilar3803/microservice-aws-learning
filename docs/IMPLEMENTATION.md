# Implementation & Architecture

## 1. Cloud fundamentals (AWS ↔ Azure)

| Purpose | AWS (used here) | Azure equivalent |
|---|---|---|
| Virtual machine to run the apps | **EC2** | Virtual Machines |
| Object/file storage | **S3** | Blob Storage |
| Serverless function | **Lambda** | Azure Functions |
| Event wiring (S3 → Lambda) | S3 Event Notification | Event Grid → Function (Blob trigger) |
| Permissions without keys | IAM Role (instance profile) | Managed Identity |
| Logs | CloudWatch Logs | Azure Monitor / App Insights |
| Firewall | Security Group | Network Security Group |

## 2. Architecture

```
            (public)                    (same EC2 instance)
 Client ──► User Service :8081 ──REST──► File Service :8082 ──SDK──► S3 bucket
 (curl/                │  (sync, blocking)                              │
  Postman)             ▼                                                │ ObjectCreated event
                  H2 users DB                                           ▼
                                                                  AWS Lambda ──► CloudWatch Logs
```

- **User Service** (Spring Boot, JPA + in-memory H2): manages users. For any file request it first
  checks the user exists, then calls the File Service and returns its answer.
- **File Service** (Spring Boot, AWS SDK v2): uploads, lists and downloads files in S3. Objects are
  stored as `{userId}/{filename}`.
- **Lambda** (Python 3.12): triggered by S3 `ObjectCreated`; logs bucket, key and size of the new file.
- **EC2**: both jars run on one instance (ports 8081 and 8082). Access to S3 is through the
  instance's IAM role, so no credentials are stored in code or config.

## 3. Synchronous communication
`FileServiceClient` (in User Service) uses Spring's `RestClient` to call the File Service. The call is
blocking: the client's request waits until the File Service has finished writing to S3. If the File
Service is unreachable the User Service returns **502 Bad Gateway**; unknown user → **404**.

## 4. Workflow (what happens on upload)
1. `POST /users/1/files` (User Service)
2. User Service verifies user 1 exists
3. User Service → `POST /files` on File Service (multipart: file + userId) — synchronous
4. File Service `PutObject` → `s3://bucket/1/hello.txt`
5. S3 emits an ObjectCreated event → Lambda runs → writes a log line in CloudWatch
6. Response with bucket/key/size travels back to the client

## 5. Verification done
Built and run locally (JDK 21) against a fake S3: create user, upload, list, download, unknown user (404),
invalid email (400) and File Service down (502) all behaved as expected; Lambda handler tested with a
sample S3 event (`lambda/test-event.json`). The real AWS parts (EC2, S3, Lambda trigger) are done via the
steps in `README.md`.

## 6. Deployment details & public URLs
- Region: `ap-southeast-2` (Sydney) · EC2 `t3.micro` (Amazon Linux 2023, Java 17) · S3 bucket `amzn-demo-s3-test-test`
- User Service: http://3.26.212.66:8081/users
- File Service: http://3.26.212.66:8082/files?userId=1
- Test data: `./scripts/seed-test-data.sh http://3.26.212.66:8081` (3 users, 4 files uploaded, round-trip checked: PASS)
- An extra upload (`1/lambda-test.txt`) was made after the Lambda S3 trigger was added to test the event.
- Note: the instance may be stopped after submission, so these URLs may stop responding.

## 7. Screenshots (`docs/screenshots/`)
- `01-live-api-responses.png` – live responses from the public URLs
- Add the AWS console ones: EC2 instance running, S3 bucket with files, Lambda trigger, CloudWatch log line from the Lambda.
