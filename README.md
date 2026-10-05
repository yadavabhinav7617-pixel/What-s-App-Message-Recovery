# NotifyVault - Privacy-First Android Notification Vault

NotifyVault is a modern, privacy-focused Android application designed to safely preserve a local history of incoming notifications (such as WhatsApp messages). It allows users to search, filter, and review saved notification copies even if the original message is deleted.

---

## Key Features

- **Automated Capture**: Listens to incoming notifications from supported messaging apps (`com.whatsapp`).
- **Privacy First**: 100% on-device local Room database storage. Zero cloud uploads, zero analytics, zero ad SDKs.
- **Search & Filter**: Search by sender or message text; filter by date ranges (Today, This Week, Older).
- **Customizable Retention**: Automatically delete entries older than 7, 30, or 90 days.
- **JSON Data Export**: Export saved notification data locally via Android Storage Access Framework.
- **Modern UI**: Polished Material 3 design system with Light, Dark, and System Default theme support.

---

## How to Get a Live Privacy Policy URL for Google Play Console (Free via GitHub Pages)

Google Play Store requires a publicly accessible HTTPS Privacy Policy URL. You can host the included `privacy.html` file on GitHub Pages for free in 3 simple steps:

### Step 1: Push Project to GitHub
1. Create a new repository on [GitHub](https://github.com) named `NotifyVault`.
2. Push your project code (including `privacy.html` in the root folder) to GitHub:
   ```bash
   git init
   git add .
   git commit -m "Initial commit - NotifyVault v1.0.0"
   git branch -M main
   git remote add origin https://github.com/<your-github-username>/NotifyVault.git
   git push -u origin main
   ```

### Step 2: Enable GitHub Pages
1. Go to your repository on GitHub (`https://github.com/<your-github-username>/NotifyVault`).
2. Click **Settings** > **Pages** (under Code and automation on the left sidebar).
3. Under **Build and deployment**:
   - **Source**: Select `Deploy from a branch`.
   - **Branch**: Select `main` and folder `/ (root)`.
4. Click **Save**.

### Step 3: Copy Your Live Privacy Policy URL
After 1–2 minutes, GitHub Pages will deploy your site. Your live Privacy Policy URL for Google Play Console will be:
```
https://<your-github-username>.github.io/NotifyVault/privacy.html
```

---

## Build & Release Requirements

- **Android Studio**: Ladybug / 2026.1+
- **Compile SDK**: 34
- **Min SDK**: 26
- **Gradle Version**: 8.9
- **Android Gradle Plugin (AGP)**: 8.7.2
- **Kotlin**: 1.9.24

## Project Setup

### Android app
1. Open the project root in Android Studio.
2. Let Gradle sync complete.
3. Confirm the app has notification access permission enabled for the installed app.
4. Keep the existing `NotificationListenerService` implementation; it is the supported capture path.
5. Use `app/src/main/AndroidManifest.xml` for the notification service registration and internet permission.

### Backend
1. Open `backend/`.
2. Copy `.env.example` to `.env` and populate the values.
3. Install dependencies with `npm install`.
4. Start locally with `npm run dev` or build with `npm run build`.

### MongoDB Atlas
1. Create a MongoDB Atlas cluster and database user.
2. Add the cluster connection string to `MONGODB_URI`.
3. Keep credentials only in the backend environment, never in the app or browser dashboard code.

### Required environment variables
Example values:
```bash
PORT=4000
MONGODB_URI=mongodb+srv://<user>:<password>@cluster.mongodb.net/notifyvault
JWT_SECRET=replace_with_secure_random_secret
ADMIN_INITIAL_SETUP_SECRET=replace_with_secure_setup_secret
CORS_ORIGIN=http://localhost:5173
SUPER_ADMIN_EMAIL=admin@notifyvault.local
SUPER_ADMIN_PASSWORD=ChangeMe123!
```

### First SUPER_ADMIN creation
The backend creates the first admin only when `ADMIN_INITIAL_SETUP_SECRET` is set and the environment variables are loaded. Use a strong password and rotate it after first sign-in.

### Device registration
Android clients must authenticate with the backend and then register their unique `deviceId` with the account using the authenticated `/api/devices/register` endpoint. Device IDs are generated per installation and never derived from IMEI or other restricted identifiers.

### Cloud Sync configuration
The app stores cloud sync settings in local SharedPreferences. Turn `Cloud Sync` on from the Android settings screen to schedule automatic uploads. When it is off, notification capture and local storage still work; uploads pause until re-enabled.

### Admin Dashboard configuration
The `admin/` static dashboard is intentionally separate from the Android app. It authenticates with the backend via JWT and should be deployed behind the same secure backend environment or an authenticated host.

### Development backend URL
- Android emulator: `http://10.0.2.2:4000`
- Local device: `http://<your-computer-ip>:4000`
- Browser admin: `http://localhost:4000`

### Production backend URL
Set `CORS_ORIGIN` and the app's backend URL to your HTTPS deployment domain, for example `https://api.notifyvault.example.com`.

### Android release build
1. In Android Studio, open **Build > Generate Signed Bundle / APK...**
2. Select **Android App Bundle**
3. Create or select a release keystore
4. Build the `release` variant and upload the generated `.aab`

### Backend deployment
1. Build: `npm run build`
2. Start: `npm run start`
3. Run behind HTTPS and a reverse proxy in production
4. Keep MongoDB credentials and JWT secret in environment variables only

### Admin Dashboard deployment
Serve the static `admin/` folder behind HTTPS and ensure it is only accessible to authenticated admin users. It does not include any direct MongoDB connectivity.

## Build & Release Requirements

### Generate Signed App Bundle (.aab)
To generate the release bundle for Play Console:
1. In Android Studio, go to **Build > Generate Signed Bundle / APK...**
2. Select **Android App Bundle**.
3. Select or create your Release Keystore (`release-key.jks`).
4. Select `release` build variant and click **Create**.
5. Upload the resulting `.aab` file located at `app/release/app-release.aab` to Google Play Console.
