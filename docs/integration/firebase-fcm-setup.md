# Firebase FCM Setup

This app can compile without Firebase project files. Real push turns on when the Android client has `app/google-services.json` and the backend has Firebase Admin credentials.

## Android

1. Open Firebase Console and create or select a Firebase project.
2. Add an Android app with package name `com.example.yingshi`.
3. Download `google-services.json`.
4. Put it at:

```text
E:\Study\App\YingShi\app\google-services.json
```

The Gradle build applies the Google Services plugin only when that file exists. Without it, the app still builds and simply skips FCM token registration.

## Backend

1. In Firebase Console, open Project settings -> Service accounts.
2. Generate a new private key for Firebase Admin SDK.
3. Store that JSON outside git.
4. For local Docker Compose, set the host JSON path and let Compose mount it into the container.

Local `.env` example:

```dotenv
FCM_ENABLED=true
FCM_DRY_RUN=false
FCM_PROJECT_ID=your-firebase-project-id
FCM_SERVICE_ACCOUNT_HOST_PATH=E:/Secrets/yingshi-firebase-adminsdk.json
FCM_SERVICE_ACCOUNT_PATH=/run/secrets/firebase-service-account.json
FCM_SERVICE_ACCOUNT_JSON_BASE64=
```

Secret-manager/base64 example:

```dotenv
FCM_ENABLED=true
FCM_PROJECT_ID=your-firebase-project-id
FCM_SERVICE_ACCOUNT_JSON_BASE64=base64-encoded-service-account-json
```

For local development, keep `FCM_SERVICE_ACCOUNT_JSON_BASE64` empty when using `FCM_SERVICE_ACCOUNT_HOST_PATH`; the backend checks base64 first when it is non-empty.

## Behavior

- App startup and login restore try to register the current FCM token with `POST /api/push/device-tokens`.
- `FirebaseMessagingService.onNewToken()` registers refreshed tokens.
- Backend photo/comment/delete and life-console mutations send visible partner notifications when the corresponding preference is enabled.
- Android receives `life_console.changed` data and refreshes both life-console widgets.
- If Firebase is not configured, backend logs and skips sending; manual refresh, foreground refresh, and the 30-minute widget refresh still work.
