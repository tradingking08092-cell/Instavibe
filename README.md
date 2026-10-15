# InstaVibe Android starter

A native Kotlin/Jetpack Compose dark-mode social app with Firebase Authentication, Firestore and Storage.

## What works
- Email/password signup and login, reset password, verification email, sign out
- Chronological live feed of the latest 100 posts
- Image uploads, video uploads (tap to play via installed external player)
- Like/unlike, comments, follow/unfollow
- Profile name and bio editing; Explore search across loaded posts

## Setup for beginners
1. Install **Android Studio** (recent stable version), Android SDK 35, and JDK 17.
2. Open Android Studio > **Open** > select this `InstaVibe` directory.
3. Visit https://console.firebase.google.com/ and create a Firebase project.
4. Add an **Android app** with package name exactly `com.instavibe.app`.
5. Download its **google-services.json** and put it in `app/google-services.json`. This file is unique to your Firebase project and is intentionally NOT bundled.
6. In Firebase Console > Authentication > Sign-in method, enable **Email/Password**.
7. Create a **Cloud Firestore** database and **Cloud Storage** bucket. Note that Firebase Storage may require the Blaze billing plan; check Firebase pricing and quotas.
8. In Firestore > Rules paste the contents of `firestore.rules` and publish. In Storage > Rules paste `storage.rules` and publish. Do NOT use permissive test rules in production.
9. Let Android Studio sync Gradle dependencies (internet required). Select a running Android emulator or USB-debugging device and press **Run**.
10. To create an APK: Android Studio > Build > Build Bundle(s) / APK(s) > Build APK(s). The debug APK appears under `app/build/outputs/apk/debug/`.
11. For a distributable signed APK use Build > Generate Signed Bundle / APK; store your signing key safely.

## Important limitations and next steps
This is a **functional MVP source project**, not a production-ready Instagram clone. It has not been compiled or device-tested in this environment. It uses an external Android video player, not an in-feed video component. Explore searches the loaded 100 posts only; no server-side discovery/ranking. No push notifications, moderation, reporting, pagination, follower counts, account deletion, or media transcoding. Account verification emails are supported but verification is not enforced before use. Profile data is publicly readable to signed-in users. Download URLs can be shared; Firebase Storage download tokens may allow access to anyone possessing the URL. For stronger media privacy use authenticated retrieval, short-lived URLs, and a controlled backend. Client-side UI checks are not security controls.

For production, add App Check, abuse/rate limits, stronger field validation, transactional counts, content moderation, a deletion/export flow, reporting/blocking, server-side media processing, and privacy/terms pages. Firebase billing and security rules must be configured by the project owner.

## Architecture
- `app/src/main/java/com/instavibe/app/MainActivity.kt` — Compose screens, Firebase integration
- `firestore.rules` — document access rules
- `storage.rules` — media access rules
- `app/build.gradle.kts` — Android dependencies

## Troubleshooting
- `google-services.json is missing`: download it from your own Firebase project and put it in `app/`.
- `PERMISSION_DENIED`: deploy both rules files and check that you are signed in.
- Storage upload fails: enable Firebase Storage, check billing/bucket setup and file size under 50 MB.
- Gradle sync fails: verify internet, Android SDK 35, JDK 17, and Gradle plugin compatibility.
