# Ludo Kid 🎲🧠

**Ludo Kid** is a modern, educational twist on the classic Ludo game. It combines the fun of strategy and board games with curated trivia facts to make learning an interactive experience for kids and adults alike.

## 🚀 Features

-   **Multiplayer Fun**: Play with 2 to 4 players.
-   **Educational Gameplay**: 
    -   Every dice roll reveals a **Trivia Fact** (Science, Space, Animals, Geography, History, etc.).
    -   To "kill" an opponent's pawn, you must answer a **multiple-choice question** based on the facts you've seen.
-   **Dynamic UI**: 
    -   Custom 15x15 Ludo board rendered on Canvas.
    -   Animated dice with real-time feedback.
    -   Sleek dark-themed Material Design interface.
-   **Game Logic**: Full Ludo rules implementation including safe zones, home stretches, and bonuses for rolling sixes.

## 🛠️ Tech Stack

-   **Language**: Kotlin
-   **Architecture**: MVVM (Model-ViewModel-Intent)
-   **UI**: Material Components, Custom Canvas Views
-   **Dependencies**: Coroutines, LiveData, Gson, Lottie (for future animations)

## 📱 Getting Started

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/chin-mayKulkarni/LudoKid.git
    ```
2.  **Open in Android Studio**:
    -   Select "Open" and navigate to the project folder.
    -   Wait for Gradle Sync to complete.
3.  **Run the app**:
    -   Connect an Android device or start an emulator.
    -   Click the **Run** button.

## 📁 Project Structure

-   `app/src/main/java/com/ludokid/data`: Data models (Player, Pawn, TriviaCard, GameState).
-   `app/src/main/java/com/ludokid/game`: Core game engine and board logic.
-   `app/src/main/java/com/ludokid/ui`: Activities, Fragments, and Custom Views.
-   `app/src/main/res/raw/trivia_cards.json`: The knowledge base containing 50+ trivia facts.

---
Created with ❤️ by **Antigravity AI** for **Chinmay Kulkarni**.
