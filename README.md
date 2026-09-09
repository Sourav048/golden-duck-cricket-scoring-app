# Golden Duck - A Cricket Scoring & League App 🏏

**Golden Duck** is a feature-rich, professional-grade cricket scoring and league management application for Android. Designed for local leagues, gully cricket, and recreational tournaments, it combines real-time ball-by-ball scoring, deep player analytics, live league chat, and real-time push notifications.

---

## 🌟 Key Features

### 💬 Live League Chat & Rich Messaging
- **In-App League Chat**: Engage in live discussions with players and managers within your joined leagues.
- **Rich Communication**:
  - **Quoted Replies**: Long-press any message to reply with context.
  - **@Mentions**: Tag fellow league members with auto-complete suggestions.
  - **Reactions & Emojis**: React to messages with custom emojis.
  - **GIF Integration**: Built-in GIF picker and search.
- **Full-Screen Media Viewer**: Zoomable photo viewer and dedicated full-screen video player for match highlights and shared media.
- **In-App Web Viewer**: Open shared web links directly within the app without leaving your chat session.

### 🔔 League & Chat Notifications
- **Real-Time Push Alerts**: Powered by OneSignal 5.x for instant match updates, score milestones, and incoming chat messages.
- **System Notification Channels**: Clean, categorized notifications with quick action intents.

### 📊 Live Scoring & Match Engine
- **Ball-by-Ball Live Scoring**: Real-time tracking of runs, extras (Wides, No-Balls, Byes, Leg-Byes), wickets, and retirements.
- **Flexible Match Modes**: Supports standard league matches, 1v1 duels, and Gully Cricket.
- **Custom Ball Types**: Stumper, Red/Green Tennis, Leather, Wind Ball, Rubber, and Plastic.
- **Duckworth-Lewis-Stern (DLS) Engine**: Dynamic target calculation for rain-affected matches.
- **Venue Suggestions**: League-isolated venue autocomplete suggestions.

### 📈 Advanced Analytics & Player Profiles
- **Radar Charts (Spider Maps)**: Visual player comparison across Strike Rate, Average, Bowling Lethality, Economy, and Fielding using MPAndroidChart.
- **Head-to-Head (H2H) Duels**: Ball-by-ball breakdown of batter vs. bowler interactions.
- **Rivalry Badges**: Automatic "Bunny", "Owner", and "Fierce Rivalry" badges based on historical match stats.
- **Prestige Rankings & Leaderboards**: Live leaderboards for Most Runs, Wickets, Best Strike Rate, Economy, 50s, 30s, 5W/3W/2W hauls, featuring Prestige status colors (Gold, Orange, Purple).

### 💾 Sync, Backup & Architecture
- **Offline-First & Local Persistence**: Built on Room Database for zero data loss during network disruptions.
- **Cloud Sync**: Firebase Firestore integration for syncing matches, rosters, and stats across devices.
- **Backup & Restore**: Export and import full app state seamlessly.

---

## 🛠 Tech Stack

- **Language**: 100% Kotlin
- **Architecture**: MVVM, Fragment-based navigation, ViewBinding
- **Database**: Room Persistence Library (SQLite) & Firebase Firestore
- **Push Notifications**: OneSignal SDK (v5.x)
- **Charts & Data Visualization**: MPAndroidChart
- **UI Components**: Material Design 3, ViewPager2, Custom Dialogs & Drawables
- **Media Loading**: Glide & ExoPlayer/VideoView
- **Animations**: Lottie & Confetti Engine

---

## 📥 Installation & Builds

Download the latest release APK (**`Golden_Duck_v5.0.apk`**) from the [Releases](https://github.com/Sourav048/golden-duck-cricket-scoring-app/releases) section.

---

Developed with ❤️ by **Sourav Sharma**
