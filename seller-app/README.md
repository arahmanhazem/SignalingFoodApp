# Milestone 2 — Seller App

Standalone Android Studio project derived from the authentication/profile design in
https://github.com/arahmanhazem/SignalingFoodApp.

Open **this seller-app directory**, not the outer repository, in a separate Android
Studio window. The outer Milestone 1 project remains unchanged. This project has its
own settings, Gradle wrapper, app module and application ID:
`com.abdelrahmanhazem.seller`. It can be installed alongside the original app.

## Scope

Implemented in source:

- Seller-only email/password registration and sign-in using Firebase Authentication.
- Saves profiles at the original `users/{uid}` path with `identity: "Seller"`.
- Checks the database role after login, on session restore and before profile saves.
  Buyer/missing-role accounts are signed out and refused entry.
- Owner name, shop name, phone and shop address editing; existing optional age is preserved.
- Password reset through Firebase email. Passwords are never stored in the database.
- System gallery/file chooser, JPEG compression, and strict upload size < 500,000 bytes.
- Private S3 upload AND authenticated download using the configured team credentials.
- Stable S3 HTTPS photo URL stored in Firebase; reloads it on the next login.
- New object key on each photo replacement, preserving both upload timestamps for evidence.
- Logout, errors and retry messages, background image/network work, and disabled controls
  while an operation is in progress. System bars remain visible for recording the clock.

The team still needs a separate Buyer project. Inventory/products/orders are outside
this seller Milestone 2 scope (inventory begins in Milestone 3). This is not a claim
that the entire team milestone or its video/screenshots are complete.

## 1. Firebase setup

1. Open the **same Firebase project** as Milestone 1.
2. Project settings > Your apps > Add app > Android.
3. Register package **com.abdelrahmanhazem.seller** exactly.
4. Download its `google-services.json` to `seller-app/app/google-services.json`.
   The original repository's JSON only registers `com.abdelrahmanhazem`; do not copy
   it unchanged. This new app must share the same Firebase project/database as Buyer.
5. Enable Email/Password under Authentication > Sign-in method if not already enabled.
6. Use the existing Realtime Database. For a database without a default URL in the
   downloaded configuration, pass its exact console URL to `FirebaseDatabase.getInstance(URL)`
   in SellerActivity.kt.
7. Review `firebase.rules.example.json` with the teammate managing database rules.
   It is a minimal M1/M2 users-only example. Merge it with existing rules; do not
   overwrite product/order/team rules. Remove broad parent-level read/write grants:
   a permissive parent grant overrides restrictive children. Publish the reviewed rules.
   The immutable identity validation matters: hiding the Buyer button alone is not
   role security. For later buyer shop browsing, add an intentionally public shop
   summary node instead of publishing private user profiles.

Existing accounts with exact `identity: "Seller"` can sign in. Missing roles are
not automatically converted, to avoid granting seller access accidentally. A trusted
team administrator can migrate known legacy sellers in Firebase. Buyer accounts must
use their Buyer application or a different email to register a separate seller account.

## 2. AWS setup (local only)

1. Copy `aws.properties.example` to `aws.properties` in this directory.
2. Enter the team's current access key, secret access key, exact bucket name and bucket
   region. Enter a session token only if using temporary credentials.
3. Never commit `aws.properties`. It is ignored by Git. Rebuild after changing it.
4. Ask the AWS administrator to grant `s3:PutObject` and `s3:GetObject` on
   `arn:aws:s3:::YOUR_BUCKET/seller-profiles/*`. An example policy is included.
   It is documentation only; the app does not create buckets or change IAM policies.
5. Leave S3 Block Public Access enabled. The app does not need public-read ACLs,
   bucket listing, or an anonymously accessible image URL. SSE-KMS buckets may also
   require KMS permissions; use the team's assigned configuration.
6. `s3:ListAllMyBuckets` is not needed for the app. If the AWS console says you cannot
   list buckets, ask the administrator for the console/listing access needed for
   evidence, or the direct bucket link and appropriate bucket listing permissions.
   App access and console browsing permissions are different.

**Course credential limitation:** this follows the PDF's requirement for team
credentials in Kotlin, with secrets kept out of source control. BuildConfig values
can still be extracted from an APK. Do not distribute this credential-bearing app;
use fresh, narrowly scoped demo credentials and rotate any previously exposed keys.
A production app needs short-lived credentials or a backend with per-user access
checks. Shared team S3 keys do not provide per-seller IAM isolation.

Every uploaded image is strictly under 500,000 bytes (conservative for 0.5 MB).
The app compresses large gallery originals before sending anything to S3.
The bucket's **total 0.5 GB** limit is a team responsibility: monitor usage and delete
unneeded demo objects after collecting evidence. Old photos are intentionally retained.
If an upload succeeds but Firebase save fails, retry Save in the same screen to reuse
the uploaded URL. Leaving the screen, force-stopping or losing the process can leave
an unreferenced S3 object; clean it up in the console after the demo.

## 3. Open and run

1. Extract the ZIP, then Android Studio > Open > select `seller-app`.
2. Add the two local configuration files above.
3. Use the same Android toolchain as the supplied repository: Android Gradle Plugin
   9.4.1, Gradle 9.6.0, compile/target SDK 37, Google Services plugin 4.5.0 and Firebase
   BoM 34.19.0. These versions are inherited, not upgraded by this change. Select a
   compatible Gradle JDK in Android Studio (the original project specifies JDK 25).
4. Let Gradle sync. Install the requested Android SDK through SDK Manager if needed.
5. Run on an API 24+ emulator/device with internet access.

Windows command line: `gradlew.bat :app:assembleDebug`

macOS/Linux: `bash gradlew :app:assembleDebug`

## 4. Demonstration and acceptance checklist

Use a test seller and two visibly different photos. Keep the device's **clock visible
throughout the video**. Do not expose aws.properties or passwords in the recording.

1. Create a seller, then confirm Authentication contains the UID and Realtime Database
   has `users/UID` with `identity: "Seller"`, name, phone, email and shop name.
2. Log in. Edit shop fields. Choose photo A, then **Save changes**. Wait for “Profile saved.”
3. In S3, show `seller-profiles/UID/…jpg`, the photo and Last modified timestamp.
4. Choose different photo B and Save changes. Show both S3 objects and both timestamps.
5. Show Firebase's new `photoUrl` and `photoUpdatedAt` under that same UID.
6. Log out. Log in using the **same account**. Photo B and the saved shop details must load.
   For stronger persistence evidence, close/reopen the app before logging back in.
7. Capture Firebase screenshots showing the user under the correct UID.
8. Repeat the required sequence in the Buyer app as part of the full team's submission.

Additional checks before submission:

- Buyer credentials are refused by Seller and the Firebase session is signed out.
- Profile text-only edits retain the saved photo URL and any M1 fields.
- A large original image is compressed and the S3 object is < 500,000 bytes.
- Network/AWS errors show an error, do not show false success and permit retry.
- Wrong AWS region/credentials are fixed locally and rebuilt; no bucket is made public.
- Both independently runnable projects are in the team repository, and the video/screenshots
  are included with the updated source submission.

## Files to explain in your evaluation

| File | Responsibility |
| --- | --- |
| `SellerActivity.kt` | Creates labeled fields/buttons; handles Firebase auth, role check, profile fetch/save and logout. |
| `ProfilePhotos.kt` | Downsamples, corrects EXIF orientation, compresses, checks byte limit, uploads/downloads private S3 objects. |
| `app/build.gradle.kts` | App ID, Firebase/AWS dependencies and local AWS configuration passed into BuildConfig. |
| `AndroidManifest.xml` | App entry point and internet permission. |
| `firebase.rules.example.json` | Example owner-only profile access and immutable role enforcement. |

Flow: sign in → fetch users/UID → verify Seller → display fields → select/compress image
locally → Save → S3 PutObject → Firebase updateChildren(photoUrl + profile fields).
Next login: Firebase photoUrl → validate bucket/UID path → authenticated S3 GetObject → ImageView.

## Verification status of this delivery

Source/JSON/XML and integration paths were reviewed. An actual Gradle build was attempted,
but this execution environment could not reach services.gradle.org to download the
repository's wrapper distribution. It also has no configured Android SDK/emulator or team
cloud credentials. Therefore APK compilation, device UI and live Firebase/S3 behavior
are **not verified here**; complete the checklist above in Android Studio. No remote
GitHub commit, IAM change, Firebase rule publication or account/photo creation was made.

References used for the S3 approach:
- https://aws-amplify.github.io/aws-sdk-android/docs/reference/com/amazonaws/services/s3/AmazonS3.html
- https://docs.aws.amazon.com/AmazonS3/latest/userguide/example-policies-s3.html
