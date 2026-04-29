# Upload API Draft

## Purpose
Reserve the contract for future direct upload to OSS or object storage without making the app upload through the main API server.

## Endpoints

### `POST /v1/uploads/token`
- use case: request a direct-upload token and object key

Request draft:

```json
{
  "fileName": "IMG_0001.JPG",
  "mimeType": "image/jpeg",
  "fileSizeBytes": 3145728,
  "mediaType": "image"
}
```

Response draft:

```json
{
  "requestId": "req_upload_token",
  "data": {
    "uploadId": "upload_001",
    "provider": "oss",
    "bucket": "placeholder-bucket",
    "objectKey": "uploads/2026/04/upload_001.jpg",
    "uploadUrl": "https://placeholder-upload.example.com",
    "accessKeyId": "placeholder-access-key",
    "policy": "placeholder-policy",
    "signature": "placeholder-signature",
    "expireAtMillis": 1777416400000
  }
}
```

### `POST /v1/uploads/{uploadId}/complete`
- use case: tell the app backend that direct upload has completed

Request draft:

```json
{
  "etag": "placeholder-etag",
  "objectKey": "uploads/2026/04/upload_001.jpg"
}
```

## Field Notes
- `provider` is a placeholder and may later support `oss`, `s3`, or similar
- this stage only reserves token and completion shapes

## Error Code Placeholders
- `UPLOAD_TOKEN_EXPIRED`
- `UPLOAD_INVALID_MIME`
- `UPLOAD_TOO_LARGE`
- `UPLOAD_NOT_FOUND`
- `NOT_IMPLEMENTED`

## Auth Placeholder
- bearer token required in principle
- no real token flow in Stage 11.1

## Stage 11.1 Draft-Only Notes
- no real upload execution
- no multipart chunk strategy yet
- no resume/retry protocol yet
