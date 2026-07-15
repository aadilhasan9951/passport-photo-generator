# Passport Photo Generator - Android App

Native Android application for generating passport photos with custom crop and background removal.

## Features

- **Image Upload**: Upload from gallery or capture from camera
- **Custom Crop**: Adjust crop area with 35:45 aspect ratio (passport size)
- **Background Selection**: Choose White or Light Blue background
- **Auto Layout**: Generate 4×6 inch print-ready sheet with 8 photos
- **Photo Rotation**: Photos rotated 90 degrees to the right
- **Download**: Save generated photo sheet to device

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- JDK 8 or higher
- Android SDK 24 (Android 7.0) or higher
- Gradle 8.2+

## Build Instructions

### 1. Open Project in Android Studio

```bash
# Navigate to the android directory
cd e:\project_4\android

# Open in Android Studio
# File -> Open -> Select the android folder
```

### 2. Sync Gradle

Android Studio will automatically prompt to sync Gradle. Click "Sync Now".

### 3. Build APK

**Debug APK:**
```
Build -> Build Bundle(s) / APK(s) -> Build APK(s)
```

**Release APK:**
```
Build -> Generate Signed Bundle / APK -> APK
```

### 4. Install on Device

Connect your Android device via USB and run:
```
Run -> Run 'app'
```

Or install the APK manually:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
android/
├── app/
│   ├── build.gradle          # App-level dependencies
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/passportphoto/generator/
│           │   ├── MainActivity.kt          # Upload screen
│           │   ├── CropActivity.kt          # Crop screen
│           │   ├── PreviewActivity.kt       # Preview & background selection
│           │   ├── ResultActivity.kt        # Result display
│           │   ├── PhotoLayoutGenerator.kt  # Layout generation
│           │   └── BackgroundRemover.kt      # Background removal (stub)
│           └── res/
│               ├── layout/
│               │   ├── activity_main.xml
│               │   ├── activity_crop.xml
│               │   ├── activity_preview.xml
│               │   └── activity_result.xml
│               ├── drawable/
│               │   ├── gradient_background.xml
│               │   ├── button_wrapper_bg.xml
│               │   └── ic_upload.xml
│               └── values/
│                   └── strings.xml
├── build.gradle             # Project-level configuration
└── settings.gradle          # Project settings
```

## Dependencies

- **AndroidX**: Core UI components
- **Material Design**: Material components
- **CameraX**: Camera functionality
- **Android Image Cropper**: Crop functionality with aspect ratio

## Background Removal

The app uses **API-based background removal** by calling the Flask backend server:

- **How it works**: Sends cropped image to Flask server, which uses `rembg` for accurate AI-based background removal
- **Advantages**: Same accuracy as web version, uses proven rembg library
- **Requirements**: Flask server must be running on the same network

### Server Setup

1. **Start the Flask server:**
   ```bash
   cd e:\project_4
   python app.py
   ```
   Server will run on `http://localhost:5000`

2. **Configure Android app:**
   - For emulator: Uses `http://10.0.2.2:5000` (automatically configured)
   - For real device: Change server URL in `ApiService.kt` to your computer's IP address
   - Example: `http://192.168.1.100:5000`

3. **Network requirements:**
   - Device and server must be on the same network
   - Firewall must allow port 5000
   - For remote deployment, use a cloud server

## Permissions

The app requires the following permissions:
- `READ_MEDIA_IMAGES` - Access to gallery (Android 13+)
- `READ_EXTERNAL_STORAGE` - Access to gallery (Android 12 and below)
- `CAMERA` - Access to camera
- `WRITE_EXTERNAL_STORAGE` - Save photos to device

## License

This project is part of the Passport Photo Generator application.

## Support

For issues or questions, please refer to the main project documentation.
