#!/usr/bin/env bash
# Seeds sample users + files through the User Service and verifies the round trip.
# Usage: ./scripts/seed-test-data.sh [BASE_URL]      (default http://localhost:8081)
set -euo pipefail

BASE_URL="${1:-http://localhost:8081}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Using $BASE_URL"

# --- sample files from public URLs ---
curl -fsSL -o "$TMP/photo.jpg"   https://picsum.photos/400/300
curl -fsSL -o "$TMP/dummy.pdf"   https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf
curl -fsSL -o "$TMP/titanic.csv" https://raw.githubusercontent.com/datasciencedojo/datasets/master/titanic.csv

# --- create users (sample data from jsonplaceholder.typicode.com/users) ---
create_user() {
  curl -fsS -X POST "$BASE_URL/users" -H 'Content-Type: application/json' \
    -d "{\"name\":\"$1\",\"email\":\"$2\"}" | sed -E 's/.*"id":([0-9]+).*/\1/'
}
U1=$(create_user "Leanne Graham"   "Sincere@april.biz")
U2=$(create_user "Ervin Howell"    "Shanna@melissa.tv")
U3=$(create_user "Clementine Bauch" "Nathan@yesenia.net")
echo "Created users: $U1 $U2 $U3"

# --- upload files (each upload: User Service -> File Service -> S3 -> Lambda) ---
upload() { echo "  upload $2 for user $1: $(curl -fsS -X POST "$BASE_URL/users/$1/files" -F "file=@$TMP/$2")"; }
upload "$U1" photo.jpg
upload "$U1" dummy.pdf
upload "$U2" titanic.csv
upload "$U3" photo.jpg

# --- verify: list + download and compare with the original ---
echo "Files of user $U1:"; curl -fsS "$BASE_URL/users/$U1/files"; echo
curl -fsS -o "$TMP/back.csv" "$BASE_URL/users/$U2/files/titanic.csv"
if cmp -s "$TMP/titanic.csv" "$TMP/back.csv"; then
  echo "PASS: downloaded titanic.csv matches the uploaded file"
else
  echo "FAIL: downloaded file differs"; exit 1
fi
echo "Done. Check the S3 bucket and the Lambda's CloudWatch logs for 4 upload events."
