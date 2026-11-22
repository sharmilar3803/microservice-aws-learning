# User Service + File Service on AWS (EC2 · S3 · Lambda)

Two small Spring Boot microservices. The **User Service** talks to the **File Service** over
synchronous REST, the File Service stores files in **S3**, and a **Lambda** runs on every upload.

```
user-service/   Spring Boot, port 8081  (users + public entry point for files)
file-service/   Spring Boot, port 8082  (uploads / reads files in S3)
lambda/         Python Lambda triggered by S3 ObjectCreated
scripts/        seed-test-data.sh (sample users + files, self-checking)
docs/           IMPLEMENTATION.md (architecture + explanation), screenshots/
```

## API (User Service, port 8081)

| Method | URL | What it does |
|---|---|---|
| POST | `/users` | create user `{"name":"Asha","email":"asha@example.com"}` |
| GET | `/users`, `/users/{id}` | list / get users |
| POST | `/users/{id}/files` | upload file (form field `file`) → calls File Service → S3 |
| GET | `/users/{id}/files` | list the user's files (File Service → S3) |
| GET | `/users/{id}/files/{filename}` | download a file from S3 |

File Service (port 8082) has `POST /files`, `GET /files?userId=`, `GET /files/{userId}/{filename}`.

## Build (needs JDK 17+; wrapper included, no Maven install needed)

```bash
cd user-service && ./mvnw clean package && cd ..
cd file-service && ./mvnw clean package && cd ..
```
(If your default `java` is 11, set `JAVA_HOME` to a 17/21 JDK first.)

## AWS setup (console)

1. **S3** → create bucket, e.g. `jmj-demo-files-<yourname>` (keep it private).
2. **IAM** → create role for **EC2** with a policy allowing `s3:PutObject`, `s3:GetObject`, `s3:ListBucket` on that bucket.
3. **EC2** → launch Amazon Linux 2023, `t3.micro`, attach the role above (Advanced → IAM instance profile).
   Security group inbound: **22** (your IP), **8081** and **8082** (anywhere, for the demo).
4. **Lambda** → create function, runtime **Python 3.12**, paste `lambda/lambda_function.py`.
   Add trigger → **S3** → your bucket → event type *All object create events* → Add.
   Logs appear in **CloudWatch → Log groups → /aws/lambda/<function>**.

## Deploy on EC2

```bash
# from your laptop
scp -i key.pem user-service/target/user-service-1.0.0.jar file-service/target/file-service-1.0.0.jar ec2-user@<EC2_PUBLIC_IP>:~

# on the EC2 box
sudo dnf install -y java-17-amazon-corretto-headless
export S3_BUCKET=<your-bucket> AWS_REGION=<your-region>
nohup java -jar file-service-1.0.0.jar > file.log 2>&1 &
export FILE_SERVICE_URL=http://localhost:8082
nohup java -jar user-service-1.0.0.jar > user.log 2>&1 &
```
No AWS keys are needed – the SDK picks up the EC2 instance role.

## End-to-end test

```bash
URL=http://<EC2_PUBLIC_IP>:8081
curl -X POST $URL/users -H 'Content-Type: application/json' -d '{"name":"Asha","email":"asha@example.com"}'
echo "hello s3" > hello.txt
curl -X POST $URL/users/1/files -F file=@hello.txt      # → file in S3, Lambda fires
curl $URL/users/1/files                                  # list
curl $URL/users/1/files/hello.txt                        # download
```
Then check: S3 console shows `1/hello.txt`, and CloudWatch shows the Lambda log line
`New file uploaded -> bucket=... key=1/hello.txt`.

## Seed test data (one command)
Creates 3 sample users, uploads a JPG, PDF and CSV (downloaded from public URLs), then downloads one back and compares it:
```bash
./scripts/seed-test-data.sh http://<EC2_PUBLIC_IP>:8081     # defaults to http://localhost:8081
```
Each upload triggers the Lambda, so you get 4 log lines in CloudWatch.

## Run locally (optional)
Needs real AWS credentials in your environment (or a fake S3 via `S3_ENDPOINT=http://localhost:4566`):
```bash
S3_BUCKET=<bucket> AWS_REGION=<region> java -jar file-service/target/file-service-1.0.0.jar
java -jar user-service/target/user-service-1.0.0.jar
```
