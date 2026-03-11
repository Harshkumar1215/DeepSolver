# Deep Solver - AI-Powered MCQ Assistant

**Developer: HARSH KUMAR**

## 📱 Project Overview
Deep Solver is an intelligent Android overlay application designed to help users solve Multiple Choice Questions (MCQs) in real-time. It runs as a floating service on top of other apps (like Chrome, PDF readers, or educational apps), reads the screen content using Accessibility Services, and uses Google's Gemini AI to identify and highlight the correct answer with a green dot.

---

## 🚀 Key Features

### 1. **Intelligent Overlay**
- **Floating Button**: A moveable, semi-transparent icon that stays on top of any app.
- **Visual Feedback**: The button changes its border color to indicate its state:
  - **White Border**: Standby Mode (App is on but not reading).
  - **Orange Border**: Active Mode (App is currently reading and solving).
- **Non-Intrusive**: Draws highlights (green dots) on a transparent layer, ensuring no interference with the underlying app's functionality.

### 2. **AI-Powered Solving**
- **Gemini 1.5 Flash Integration**: Uses Google's latest generative AI to understand complex questions and context.
- **Universal Format Support**: Identifies MCQs in any format:
    - Lettered (A, B, C, D)
    - Numbered (1, 2, 3, 4)
    - Plain text (next to checkboxes or radio buttons)
- **Fuzzy Matching**: Capable of mapping AI text answers back to specific screen elements even with symbol or spacing differences.

### 3. **Smart Rate Limiting**
- **10-Second Interval**: Automatically waits 10 seconds between solving requests to ensure smooth performance and manage API usage.
- **User Toasts**: Real-time status updates like "Solving..." and "Answer Found!" keep the user informed.

### 4. **Privacy & Security**
- **Guided Onboarding**: Explains why Accessibility and Overlay permissions are needed before requesting them.
- **Local Context**: Processes screen text temporarily to find answers; no data is stored or shared.

---

## 📂 Project Structure & Code Files

### 1. `MainActivity.java`
The command center of the app.
- Handles the **User Interface** (Material Design 3).
- Manages **Permissions** (System Alert Window & Accessibility).
- Provides shortcuts to **App Settings** for MIUI/Xiaomi users.

### 2. `OverlayService.java`
The visual layer of the app.
- Runs as a **Foreground Service** to prevent the system from killing it.
- Manages the **Floating Button** UI and its touch interactions (dragging/tapping).
- Responsible for drawing the **Green Dot Highlights** at specific screen coordinates.

### 3. `DeepSolverAccessibilityService.java`
The "brain" of the application.
- Uses **Accessibility APIs** to traverse the view hierarchy and extract all screen text.
- Connects to the **Gemini AI SDK** to process the extracted text.
- Maps the AI's answer back to the **X and Y coordinates** on the user's screen.

### 4. `AndroidManifest.xml`
- Declares the required permissions: `INTERNET`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`.
- Configures the **Accessibility Service** intent filters and meta-data.

---

## 🛠️ Setup Instructions
1.  **Firebase**: The app is integrated with Firebase. Ensure `google-services.json` is present in the `app/` directory.
2.  **API Key**: The Gemini AI key is integrated within the `DeepSolverAccessibilityService.java` file.
3.  **Permissions**:
    - Enable **Display over other apps** (Overlay).
    - Enable **Deep Solver AI Assistant** in Accessibility settings.
    - (For Xiaomi) Enable **Display pop-up windows while running in the background**.

---
**Deep Solver** - *Making learning and testing smarter.*
