import json
import urllib.parse


def lambda_handler(event, context):
    """Triggered by an S3 ObjectCreated event. Logs details of each uploaded file."""
    for record in event.get("Records", []):
        bucket = record["s3"]["bucket"]["name"]
        key = urllib.parse.unquote_plus(record["s3"]["object"]["key"])
        size = record["s3"]["object"].get("size", 0)
        print(f"New file uploaded -> bucket={bucket} key={key} size={size} bytes")

    return {"statusCode": 200, "body": json.dumps("Processed S3 upload event")}
