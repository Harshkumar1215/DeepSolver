# Deep Solver - AI-Powered MCQ Assistant

**Developer: HARSH KUMAR**

## 📱 Project Overview
Deep Solver is an intelligent Android overlay application designed to help users solve Multiple Choice Questions (MCQs) in real-time. It runs as a floating service on top of other apps, automatically identifies questions on your screen using Accessibility Services, and retrieves answers using a built-in search engine powered by **Jsoup**.

Unlike previous versions, it no longer requires manual selection; it detects questions automatically and displays the most relevant results directly in a clean, Material Design popup.

---

## 🚀 Key Features

### 1. **Automatic Question Detection (Lens Mode)**
- **Smart Scanning**: Uses Accessibility APIs to traverse the screen and find text blocks containing question marks (`?`) or the most prominent text content.
- **Floating Button**: A moveable icon that toggles the "Lens Mode".
  - **Grey/Standard Icon**: Standby Mode.
  - **Active Icon**: Scanning and Solving Mode.

### 2. **Search Engine Powered Solving**
- **Jsoup Integration**: A 100% Java-based implementation that scrapes Google Search results to find Featured Snippets and top relevant answers.
- **Robust Parsing**: Extracts "Best Match" snippets, which are often direct answers to common MCQs.
- **No Python Required**: Fully migrated from Chaquopy to native Java for better performance and smaller app size.

### 3. **Non-Intrusive Result Display**
- **Direct Answer Popup**: No more hunting for dots. Answers appear in a scrollable Material Design card at the bottom of the screen.
- **Clean UI**: Uses Material Design 3 components (`MaterialCardView`, `NestedScrollView`) for a modern look and feel.

### 4. **Privacy & Security**
- **Guided Onboarding**: Explains why Accessibility and Overlay permissions are needed.
- **Local Processing**: Processes screen text temporarily to perform searches; no data is stored or shared.

---

## 📂 Project Structure & Code Files

### 1. `MainActivity.java`
- Manages the UI and initial setup.
- Handles complex **Permissions** (Overlay & Accessibility) with a clean user-guided flow.
- Automatically synchronizes with the service state.

### 2. `OverlayService.java`
- Runs as a **Foreground Service** with a persistent notification.
- Manages the **Floating Button** UI and dragging logic.
- Acts as a bridge between the screen content and the user interaction.

### 3. `DeepSolverAccessibilityService.java`
- The core logic for **Screen Text Extraction**.
- Implements the **Automatic Question Identification** algorithm.
- Handles the **Jsoup Web Scraping** logic on a background thread.
- Manages the lifecycle of the **Result Popup Window**.

### 4. `AndroidManifest.xml`
- Declares `INTERNET`, `SYSTEM_ALERT_WINDOW`, and `FOREGROUND_SERVICE` permissions.
- Configures the Accessibility Service meta-data.

---

## 🛠️ Setup Instructions
1.  **Dependencies**: The app uses `org.jsoup:jsoup` for web scraping. Ensure a successful Gradle sync.
2.  **Permissions**:
    - Enable **Display over other apps** (Overlay).
    - Enable **Deep Solver AI Assistant** in Accessibility settings.
    - (For Xiaomi/MIUI) Enable **Display pop-up windows while running in the background** in app info.
3.  **Usage**:
    - Open the app and click **Start Service**.
    - Navigate to a question and tap the **Floating Icon**.
    - View the answer in the popup and close it when done.

---
**Deep Solver** - *Making learning and testing smarter.*
