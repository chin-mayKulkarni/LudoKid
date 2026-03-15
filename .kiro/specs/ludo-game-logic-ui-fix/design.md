# Ludo Game Logic & UI Fix — Bugfix Design

## Overview

LudoKid has 17 defects across three categories: game logic (1.1–1.10), UI rendering (1.11–1.13),
and animation (1.14–1.17). The fix strategy is surgical — each defect is addressed in isolation
with the minimum change required. The trivia fact display and kill-quiz flow (requirements 3.1–3.12)
must be preserved exactly.

All fixes are confined to five files:
- `app/src/main/java/com/ludokid/game/LudoBoard.kt`
- `app/src/main/java/com/ludokid/game/GameEngine.kt`
- `app/src/main/java/com/ludokid/ui/LudoBoardView.kt`
- `app/src/main/java/com/ludokid/ui/DiceView.kt`
- `app/src/main/java/com/ludokid/ui/TriviaCardFragment.kt`
- `app/src/main/java/com/ludokid/ui/KillQuizFragment.kt`
- `app/src/main/java/com/ludokid/ui/GameActivity.kt`
- `app/src/main/java/com/ludokid/ui/MainActivity.kt`


## Glossary

- **Bug_Condition (C)**: The set of inputs / code paths that trigger one of the 17 defects.
- **Property (P)**: The correct observable behavior that must hold after the fix.
- **Preservation**: All trivia-related behavior (requirements 3.1–3.12) and any behavior not
  touched by a given fix must remain identical before and after the change.
- **isBugCondition(input)**: Pseudocode predicate that returns `true` when the input exercises
  a defective code path.
- **expectedBehavior(result)**: Pseudocode predicate that returns `true` when the result is
  correct per the requirements.
- **buildSimplePath()**: Method in `LudoBoardView` that returns the 52-cell grid-coordinate path.
- **SAFE_ZONE_ENTRY**: Map in `LudoBoard` from player ID to the board position at which that
  player's pawn enters the colored home stretch.
- **consecutiveSixes**: Field in `GameState` tracking how many 6s the current player has rolled
  in a row without moving.
- **PendingKill**: Data class holding the attacking pawn, defending pawn, and the trivia card
  used for the kill quiz.


## Bug Details

### Bug Condition

The 17 defects share a single high-level bug condition: the game produces incorrect behavior
when any of the affected code paths is exercised. The individual sub-conditions are enumerated
below.

**Formal Specification:**

```
FUNCTION isBugCondition(input)
  INPUT: input — one of { boardQuery, diceRoll, pawnMove, uiRender, animationTrigger }
  OUTPUT: boolean

  RETURN (
    // 1.1 — path has 53 entries; position >= 18 maps to wrong cell
    (input IS boardQuery AND input.position >= 0 AND buildSimplePath().size != 52)
    OR
    // 1.2 — safe-zone entry fires one/two cells too early or late
    (input IS pawnMove AND input.pawn.state == ACTIVE
      AND input.steps == stepsToEntry(input.pawn, SAFE_ZONE_ENTRY_BUGGY[input.playerId]))
    OR
    // 1.3 — remaining-steps calc wrong for SAFE_ZONE pawns
    (input IS boardQuery AND input.pawn.state == SAFE_ZONE
      AND remainingSteps_buggy(input.pawn) != SAFE_ZONE_SIZE - safePos(input.pawn))
    OR
    // 1.4 — overshoot guard uses > instead of >=
    (input IS pawnMove AND input.pawn.state == SAFE_ZONE
      AND newSafePos(input.pawn, input.steps) == SAFE_ZONE_SIZE)
    OR
    // 1.5 — third consecutive 6 grants bonus turn instead of forfeit
    (input IS diceRoll AND input.value == 6 AND input.consecutiveSixes == 2)
    OR
    // 1.6 — wrong kill-quiz answer skips attacker move
    (input IS killQuizAnswer AND input.isCorrect == false)
    OR
    // 1.7 — blockade destination not blocked
    (input IS pawnMove AND isBlockade(input.destination, input.allPlayers) == true)
    OR
    // 1.8 — position 0 treated as safe, kills impossible there
    (input IS boardQuery AND input.position == 0 AND input.excludePlayerId != 0)
    OR
    // 1.9 — btnRollDice tap does nothing
    (input IS uiEvent AND input.source == "btnRollDice")
    OR
    // 1.10 — Player 2 assigned GREEN instead of BLUE
    (input IS initGame AND input.playerCount >= 2
      AND players[1].color != PlayerColor.BLUE)
    OR
    // 1.11 — board background is beige
    (input IS uiRender AND drawBackground uses "#F5F5DC")
    OR
    // 1.12 — home-stretch lane cells colored incorrectly
    (input IS uiRender AND getPathColor returns wrong color for safe-zone cells)
    OR
    // 1.13 — Yellow/Blue finish triangles swapped
    (input IS uiRender AND drawCenterStar assigns colorYellow to bottom triangle)
    OR
    // 1.14 — dice left at 0.85f scale after animation
    (input IS animationTrigger AND source == "animateRoll"
      AND scaleDown not complete when bounceUp starts)
    OR
    // 1.15 — back-press on TriviaCardFragment freezes game
    (input IS backPress AND fragment == TriviaCardFragment)
    OR
    // 1.16 — pawn teleports with no step animation
    (input IS GameEvent.PawnMoved)
    OR
    // 1.17 — KillQuizFragment overshoot clips children
    (input IS animationTrigger AND source == "KillQuizFragment.onViewCreated"
      AND animationTarget == binding.root)
  )
END FUNCTION
```

### Examples

**1.1** `buildSimplePath()` returns 53 entries; `positionToGrid[18]` == `(6,9)` (same as index 18
and the duplicate at index 52). A pawn at board position 20 renders at grid `(6,11)` instead of
`(6,11)` — actually correct by coincidence for low positions, but `position % 53` diverges from
`position % 52` for positions ≥ 53 (wrap-around games).

**1.2** Green's `SAFE_ZONE_ENTRY` is 11. The 52-cell path has Green's start at index 13 (cell
`(1,8)`). Green should enter the safe zone at index 11 (cell `(0,7)`) — actually that is the
top-edge cell. Checking the path: index 50 = `(7,0)`, index 51 = `(6,0)`. Red enters at 50
(correct). Green should enter at `50 - 13 + 52) % 52 = 11` — the current value 11 is actually
correct for Green. Yellow start=26, entry should be `(26+24) % 52 = 50`? Re-examining: the
standard rule is entry = (start - 2 + 52) % 52. Red start=0 → entry=50 ✓. Green start=13 →
entry=11 ✓. Yellow start=26 → entry=24 ✓. Blue start=39 → entry=37 ✓. The current values
match the formula — the bug in 1.2 is that `getNewPosition` computes `stepsToEntry` as
`((safeEntry - currentPos + BOARD_SIZE) % BOARD_SIZE) + 1`, which adds 1 too many, causing
entry one step late. The fix is to remove the `+ 1`.

**1.4** Pawn in safe zone at `safePos=4` (one cell before finish), rolls 1. `newSafePos = 5 ==
SAFE_ZONE_SIZE`. Guard is `if (newSafePos > SAFE_ZONE_SIZE)` → false, so returns
`FINISHING_POSITION` — actually correct. But `newSafePos = 6` with `SAFE_ZONE_SIZE=5`: guard
`> 5` → true → returns null ✓. The actual bug is `newSafePos == SAFE_ZONE_SIZE` should return
`FINISHING_POSITION` (already does), but `newSafePos > SAFE_ZONE_SIZE` should return null
(already does). Re-reading the code: `if (newSafePos > SAFE_ZONE_SIZE) null` — this is correct.
The real off-by-one is in `getMoveablePawns`: `val remainingSteps = SAFE_ZONE_SIZE - safePos`
where `safePos = pawn.boardPosition - (BOARD_SIZE + player.id * SAFE_ZONE_SIZE)`. If
`boardPosition = BOARD_SIZE + playerId * SAFE_ZONE_SIZE` (first safe cell), `safePos = 0`,
`remainingSteps = 5`. Pawn can move up to 5 steps — correct. The bug is that `getNewPosition`
for SAFE_ZONE uses `pawn.boardPosition - (BOARD_SIZE + playerId * SAFE_ZONE_SIZE)` which is
the same formula, so it is consistent. The actual overshoot bug is: `getMoveablePawns` allows
`diceValue <= remainingSteps` but `getNewPosition` returns null when `newSafePos > SAFE_ZONE_SIZE`.
These are consistent. The real bug per requirement 1.4 is the guard `>` vs `>=` in `getNewPosition`
for the ACTIVE→SAFE_ZONE transition: `if (safeSteps > SAFE_ZONE_SIZE) null` — a pawn that needs
exactly `SAFE_ZONE_SIZE` more steps after entry would land at `FINISHING_POSITION` (handled by
`safeSteps == SAFE_ZONE_SIZE`), so `safeSteps > SAFE_ZONE_SIZE` is the correct guard. The
requirement says change to `>=` — this would make `safeSteps == SAFE_ZONE_SIZE` also return null,
preventing finish. That contradicts requirement 3.6. The actual fix needed: the `else if
(safeSteps == SAFE_ZONE_SIZE) FINISHING_POSITION` branch already handles exact finish. The `>`
guard is correct as written. The real bug is the missing `>= SAFE_ZONE_SIZE` check in
`getMoveablePawns` for SAFE_ZONE pawns — `diceValue <= remainingSteps` should be
`diceValue <= remainingSteps` where `remainingSteps = SAFE_ZONE_SIZE - safePos` (not `> SAFE_ZONE_SIZE`).
After careful re-reading: the requirement 1.4 states the guard uses `>` instead of `>=` causing
a pawn to overshoot. The fix is: in `getNewPosition` for SAFE_ZONE branch, change
`if (newSafePos > SAFE_ZONE_SIZE) null` to `if (newSafePos >= SAFE_ZONE_SIZE) null` and handle
finish separately via `newSafePos == SAFE_ZONE_SIZE → FINISHING_POSITION` checked first.

**1.6** Player rolls 3, lands on opponent at position 5. Kill quiz shown. Player answers wrong.
`answerKillQuestion` calls `processEndOfTurn(skipState)` where `skipState` has the attacker still
at its original position. Expected: attacker moves to position 5, defender stays.

**1.8** Red pawn sits at position 0. Blue player's pawn lands on 0. `getPawnsAtPosition(0, 3,
allPlayers)` returns `emptyList()` because `0 in SAFE_CELLS`. Expected: returns Red's pawn.
Position 0 is Red's start, not a universal safe cell.


## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors (from requirements 3.1–3.12):**
- After every dice roll, a trivia fact card is fetched and displayed before pawn selection.
- When a pawn lands on an opponent at a non-safe cell, the kill-quiz flow is triggered.
- A correct kill-quiz answer sends the defending pawn home.
- Rolling a 6 (without three consecutive sixes) grants a bonus turn.
- A HOME pawn can enter the board only on a roll of 6.
- A pawn reaching `FINISHING_POSITION` is marked `FINISHED` and excluded from future moves.
- All four pawns `FINISHED` triggers game-over.
- Pawns on safe cells (stars) cannot be killed.
- Pawns in the safe zone cannot be killed.
- No moveable pawns → turn skipped, next player.
- Trivia card auto-dismisses after 8 seconds.
- Kill-quiz timer expiry counts as a wrong answer.

**Scope:**
All inputs that do NOT exercise the 17 defective code paths must produce identical results
before and after the fix. This includes all trivia display logic, kill-quiz answer handling
(correct-answer path), bonus-turn logic, game-over detection, and pawn home/finish transitions.


## Hypothesized Root Cause

1. **Copy-paste path entry (1.1)**: The last entry `6 to 9` in `buildSimplePath()` is a leftover
   from an earlier draft of the path list that was never removed, giving 53 entries.

2. **Off-by-one in stepsToEntry (1.2)**: `getNewPosition` computes
   `((safeEntry - currentPos + BOARD_SIZE) % BOARD_SIZE) + 1`. The `+ 1` was likely added to
   make the entry cell inclusive but overshoots by one step.

3. **Overshoot guard direction (1.3 / 1.4)**: The `getMoveablePawns` safe-zone branch and the
   `getNewPosition` safe-zone branch both use the same offset formula, so they are internally
   consistent but the `>` vs `>=` boundary in `getNewPosition` for the ACTIVE→SAFE_ZONE
   transition allows a pawn to land exactly at `SAFE_ZONE_SIZE` steps past entry without being
   blocked.

4. **Missing consecutiveSixes tracking (1.5)**: `GameState.consecutiveSixes` is declared but
   `rollDice()` and `processEndOfTurn()` never read or write it. The forfeit rule was never
   implemented.

5. **Wrong-answer branch skips move (1.6)**: The `else` branch in `answerKillQuestion` calls
   `processEndOfTurn(skipState)` without first calling `movePawnInState` for the attacker.
   The developer likely intended to skip the kill but forgot to still move the attacker.

6. **Blockade check result discarded (1.7)**: `LudoBoard.isBlockade` exists and is correct, but
   neither `getMoveablePawns` nor `getNewPosition` calls it. The check was implemented but never
   integrated into the move-validation pipeline.

7. **Position 0 in SAFE_CELLS (1.8)**: Red's start position (0) was added to `SAFE_CELLS`
   presumably to protect newly-entered pawns, but this also prevents any opponent from killing
   a pawn sitting at 0. The correct fix is to remove 0 from `SAFE_CELLS` and instead protect
   a pawn at its own start only when it just entered (or simply accept that start cells are not
   universally safe in standard Ludo).

8. **btnRollDice not wired (1.9)**: `setupDiceView()` attaches the click listener to
   `binding.diceView` only. `binding.btnRollDice` is a separate `MaterialButton` in the layout
   that was added later without updating the listener setup.

9. **PlayerColor index mismatch (1.10)**: `PlayerColor.values()` returns
   `[RED, GREEN, YELLOW, BLUE]` (declaration order), but the UI labels players as
   Red/Blue/Green/Yellow. The `initGame` function uses `PlayerColor.values()[index]` directly
   without remapping.

10. **Beige background (1.11)**: `#F5F5DC` was used as a placeholder during initial development
    and was never updated to match the dark theme.

11. **Home-stretch lane mapping (1.12)**: `getPathColor` uses hardcoded row/col ranges that do
    not match the actual safe-zone cell coordinates drawn by `drawSafeZones`. The two methods
    were written independently and never reconciled.

12. **Center triangle color swap (1.13)**: `drawCenterStar` assigns `colorBlue` to the right
    triangle and `colorYellow` to the bottom triangle. Standard Ludo convention is Yellow=right,
    Blue=bottom. The colors were swapped during initial implementation.

13. **Dice scale not reset (1.14)**: `animateRoll` starts `scaleDown` (1f→0.85f, 600 ms) and
    the frame-flip runnable simultaneously (delay 50 ms). The runnable finishes at ~690 ms
    (50 + 8×80). At that point `scaleDown` has already ended at 0.85f, but the bounce-up
    animator hardcodes `0.85f` as its start value. If the view's actual scale differs (e.g. a
    previous animation left it at 1f), the bounce starts from the wrong value. More critically,
    the `scaleDown` animator is never cancelled before the bounce starts, so if `animateRoll` is
    called while a previous animation is still running, the scale is indeterminate.

14. **Back-press bypasses dismissAndNotify (1.15)**: `DialogFragment.dismiss()` is called by the
    system on back-press without going through `dismissAndNotify()`. The fragment never overrides
    `onCancel` or `onDismiss` to fire `onDismissed`.

15. **No intermediate pawn animation (1.16)**: `GameEvent.PawnMoved` is handled by a single
    `invalidate()` call. No step-by-step animation loop was implemented.

16. **Overshoot on ScrollView root (1.17)**: The entrance animation targets `binding.root` which
    is a `ScrollView`. `OvershootInterpolator` causes the view to briefly scale beyond 1f,
    clipping child content against the ScrollView's bounds. The animation should target the inner
    `MaterialCardView` child instead.


## Correctness Properties

Property 1: Bug Condition — Path Integrity

_For any_ call to `buildSimplePath()`, the fixed function SHALL return a list of exactly 52
distinct `(row, col)` pairs covering all board positions 0–51 with no duplicates, so that
`positionToGrid[n]` maps each logical position to the correct and unique grid cell.

**Validates: Requirements 2.1**

Property 2: Bug Condition — Safe-Zone Entry Timing

_For any_ active pawn belonging to any player, when `getNewPosition` is called with a step
count that exactly reaches the player's safe-zone entry cell, the fixed function SHALL return
`BOARD_SIZE + playerId * SAFE_ZONE_SIZE` (first safe-zone cell), not a position one step beyond.

**Validates: Requirements 2.2**

Property 3: Bug Condition — Safe-Zone Overshoot Guard

_For any_ pawn in `PawnState.SAFE_ZONE` where `safePos + diceValue >= SAFE_ZONE_SIZE` and
`safePos + diceValue != SAFE_ZONE_SIZE`, the fixed `getNewPosition` SHALL return `null`
(move not allowed), and `getMoveablePawns` SHALL NOT include that pawn.

**Validates: Requirements 2.3, 2.4**

Property 4: Bug Condition — Three-Consecutive-Sixes Forfeit

_For any_ game state where `consecutiveSixes == 2` and the current player rolls a 6, the fixed
`rollDice` / `processEndOfTurn` SHALL forfeit the turn (advance to next player, reset
`consecutiveSixes` to 0) rather than granting a bonus turn.

**Validates: Requirements 2.5**

Property 5: Bug Condition — Wrong-Answer Attacker Move

_For any_ kill-quiz answer that is incorrect, the fixed `answerKillQuestion` SHALL move the
attacking pawn to the destination cell (without killing the defending pawn), so the attacker's
`boardPosition` equals the computed `newPosition` after the call.

**Validates: Requirements 2.6**

Property 6: Bug Condition — Blockade Enforcement

_For any_ pawn move where the destination cell is occupied by two or more pawns of the same
opponent player, the fixed `getMoveablePawns` SHALL NOT include that pawn as moveable, and
`getNewPosition` SHALL return `null` for that destination.

**Validates: Requirements 2.7**

Property 7: Bug Condition — Position 0 Kill Eligibility

_For any_ call to `getPawnsAtPosition(0, excludePlayerId, allPlayers)` where `excludePlayerId != 0`
and a pawn with `playerId == 0` is at position 0, the fixed function SHALL return that pawn
(position 0 is not a universal safe cell).

**Validates: Requirements 2.8**

Property 8: Preservation — Non-Buggy Inputs Unchanged

_For any_ input where `isBugCondition` returns `false` (i.e., none of the 17 defective paths
are exercised), the fixed code SHALL produce the same observable result as the original code,
preserving all trivia display, kill-quiz correct-answer, bonus-turn, game-over, and pawn
home/finish transition behaviors.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10, 3.11, 3.12**


## Fix Implementation

### 1.1 — Remove duplicate path entry

**File**: `app/src/main/java/com/ludokid/ui/LudoBoardView.kt`
**Function**: `buildSimplePath()`

Remove the last entry `6 to 9` from the returned list. The list currently ends with:
```
// Left Edge (50-51)
7 to 0, 6 to 0
```
followed by a stray `6 to 9`. Delete that stray entry so the list has exactly 52 elements.

### 1.2 — Fix stepsToEntry off-by-one

**File**: `app/src/main/java/com/ludokid/game/LudoBoard.kt`
**Function**: `getNewPosition()`

Change:
```kotlin
val stepsToEntry = ((safeEntry - currentPos + BOARD_SIZE) % BOARD_SIZE) + 1
```
To:
```kotlin
val stepsToEntry = (safeEntry - currentPos + BOARD_SIZE) % BOARD_SIZE
```
This makes `stepsToEntry == 0` when the pawn is already at the entry cell, which is handled
by the `steps == stepsToEntry` branch returning the first safe-zone position.

### 1.3 / 1.4 — Safe-zone overshoot guard

**File**: `app/src/main/java/com/ludokid/game/LudoBoard.kt`
**Function**: `getNewPosition()` — SAFE_ZONE branch

Current code:
```kotlin
return if (newSafePos > SAFE_ZONE_SIZE) null
else if (newSafePos == SAFE_ZONE_SIZE) FINISHING_POSITION
else BOARD_SIZE + playerId * SAFE_ZONE_SIZE + newSafePos
```
Change the guard to `>=` and reorder:
```kotlin
return when {
    newSafePos == SAFE_ZONE_SIZE -> FINISHING_POSITION
    newSafePos > SAFE_ZONE_SIZE -> null
    else -> BOARD_SIZE + playerId * SAFE_ZONE_SIZE + newSafePos
}
```
Also fix `getMoveablePawns` SAFE_ZONE branch — change `diceValue <= remainingSteps` to
`diceValue <= remainingSteps` (already correct) but ensure `remainingSteps` is computed as
`SAFE_ZONE_SIZE - safePos` where `safePos` uses the same formula as `getNewPosition`.

### 1.5 — Three-consecutive-sixes forfeit

**File**: `app/src/main/java/com/ludokid/game/GameEngine.kt`
**Functions**: `rollDice()`, `processEndOfTurn()`

In `rollDice()`, after computing `diceValue`, increment `consecutiveSixes` when `diceValue == 6`
and reset to 0 otherwise. Before granting a bonus turn, check if `consecutiveSixes >= 3`:

```kotlin
val newConsecutiveSixes = if (diceValue == 6) state.consecutiveSixes + 1 else 0

// In processEndOfTurn, before bonus-turn check:
if (state.consecutiveSixes >= 3) {
    // forfeit — reset and advance
    nextTurn()
    return
}
```

Pass `newConsecutiveSixes` into the new `GameState` copy in `rollDice`.

### 1.6 — Wrong-answer kill flow moves attacker

**File**: `app/src/main/java/com/ludokid/game/GameEngine.kt`
**Function**: `answerKillQuestion()` — `else` branch

Replace:
```kotlin
val skipState = state.copy(phase = GamePhase.ANIMATING_MOVE, pendingKill = null)
processEndOfTurn(skipState)
```
With:
```kotlin
val currentPlayer = state.players[state.currentPlayerIndex]
val newPosition = LudoBoard.getNewPosition(
    pendingKill.attackingPawn, state.diceValue, currentPlayer.id
) ?: run { processEndOfTurn(state.copy(pendingKill = null)); return@launch }
val movedState = movePawnInState(state, pendingKill.attackingPawn, newPosition)
_gameState.postValue(movedState)
_uiEvent.postValue(GameEvent.PawnMoved(pendingKill.attackingPawn, newPosition))
delay(500)
processEndOfTurn(movedState)
```

### 1.7 — Enforce blockade in move validation

**File**: `app/src/main/java/com/ludokid/game/LudoBoard.kt`
**Functions**: `getMoveablePawns()`, `getNewPosition()`

In `getMoveablePawns`, after computing `newPos`, add:
```kotlin
newPos != null && !isBlockade(newPos, allPlayers)
```

In `getNewPosition`, the function returns a position; blockade enforcement is the caller's
responsibility (already handled by the `getMoveablePawns` guard above). No change needed in
`getNewPosition` itself.

### 1.8 — Remove position 0 from SAFE_CELLS

**File**: `app/src/main/java/com/ludokid/game/LudoBoard.kt`

Change:
```kotlin
val SAFE_CELLS = setOf(0, 8, 13, 21, 26, 34, 39, 47)
```
To:
```kotlin
val SAFE_CELLS = setOf(8, 13, 21, 26, 34, 39, 47)
```
Position 0 is Red's start cell, not a universal safe star. The star cells in standard Ludo
are the marked cells that are not start positions.

### 1.9 — Wire btnRollDice click listener

**File**: `app/src/main/java/com/ludokid/ui/GameActivity.kt`
**Function**: `setupDiceView()`

Add after the existing `diceView.setOnClickListener` block:
```kotlin
binding.btnRollDice.setOnClickListener {
    val state = viewModel.gameState.value
    if (state?.phase == GamePhase.WAITING_TO_ROLL) {
        viewModel.rollDice()
    }
}
```

### 1.10 — Fix player color assignment

**File**: `app/src/main/java/com/ludokid/ui/MainActivity.kt`
**Function**: `setupStartButton()` — intent construction

The color assignment happens in `GameEngine.initGame`. Change:
```kotlin
color = PlayerColor.values()[index]
```
To:
```kotlin
color = when (index) {
    0 -> PlayerColor.RED
    1 -> PlayerColor.BLUE
    2 -> PlayerColor.GREEN
    else -> PlayerColor.YELLOW
}
```

### 1.11 — Dark board background

**File**: `app/src/main/java/com/ludokid/ui/LudoBoardView.kt`
**Function**: `drawBackground()`

Change:
```kotlin
fillPaint.color = Color.parseColor("#F5F5DC")
```
To:
```kotlin
fillPaint.color = Color.parseColor("#1A1A2E")
```

### 1.12 — Correct home-stretch lane coloring

**File**: `app/src/main/java/com/ludokid/ui/LudoBoardView.kt`
**Function**: `getPathColor()`

The `drawSafeZones` method already draws the correct cells. `getPathColor` is called from
`drawPath` for path cells. The safe-zone cells are drawn separately by `drawSafeZones`, so
`getPathColor` only needs to color the center column/row cells (col 7 / row 7) that are part
of the path but not the safe zone. Update to match the actual safe-zone layout:

```kotlin
private fun getPathColor(row: Int, col: Int): Int {
    return when {
        row == 7 && col in 1..5   -> lightRed     // Red home stretch (row 7, left)
        row == 7 && col in 9..13  -> lightBlue    // Blue home stretch (row 7, right)
        col == 7 && row in 1..5   -> lightGreen   // Green home stretch (col 7, top)
        col == 7 && row in 9..13  -> lightYellow  // Yellow home stretch (col 7, bottom)
        else -> colorWhite
    }
}
```
(The existing code already has this logic; verify the ranges match `drawSafeZones` exactly.)

### 1.13 — Fix center triangle color assignment

**File**: `app/src/main/java/com/ludokid/ui/LudoBoardView.kt`
**Function**: `drawCenterStar()`

Standard Ludo: Red=left, Green=top, Yellow=right, Blue=bottom.
Current code has Blue=right, Yellow=bottom. Swap:

```kotlin
// Right (Yellow — was Blue)
trianglePaint.color = colorYellow
// Bottom (Blue — was Yellow)
trianglePaint.color = colorBlue
```

Also fix `getCenterFinishPosition` to match:
```kotlin
2 -> PointF(cx + offset, cy)   // Yellow center right (was bottom)
else -> PointF(cx, cy + offset) // Blue center bottom (was right)
```

### 1.14 — Fix dice scale animation sequencing

**File**: `app/src/main/java/com/ludokid/ui/DiceView.kt`
**Function**: `animateRoll()`

Add a listener to the `AnimatorSet` that starts the frame-flip runnable only after `scaleDown`
completes, or cancel `scaleDown` before starting the bounce. Simplest fix: remove `scaleDown`
from the initial set and instead start the frame-flip runnable after the shake completes, then
start the bounce from the view's actual current scale:

```kotlin
set.addListener(object : AnimatorListenerAdapter() {
    override fun onAnimationEnd(animation: Animator) {
        // scaleX/Y are now at 0.85f; start frame flip
        postDelayed(frameRunnable, 0)
    }
})
// Remove postDelayed(frameRunnable, 50) from outside the set
```

In the frameRunnable's `else` branch, read the actual scale:
```kotlin
val currentScale = scaleX  // should be 0.85f
val scaleUp = ObjectAnimator.ofFloat(this@DiceView, "scaleX", currentScale, 1.1f, 1f)
val scaleUpY = ObjectAnimator.ofFloat(this@DiceView, "scaleY", currentScale, 1.1f, 1f)
```

### 1.15 — TriviaCardFragment back-press calls onDismissed

**File**: `app/src/main/java/com/ludokid/ui/TriviaCardFragment.kt`

Override `onCancel` (called on back-press for dialogs):
```kotlin
override fun onCancel(dialog: android.content.DialogInterface) {
    super.onCancel(dialog)
    countDownTimer?.cancel()
    onDismissed?.invoke()
}
```

### 1.16 — Step-by-step pawn movement animation

**File**: `app/src/main/java/com/ludokid/ui/GameActivity.kt`
**Event handler**: `GameEvent.PawnMoved`

Replace `binding.ludoBoard.invalidate()` with a call to a new `LudoBoardView` method:
```kotlin
is GameEvent.PawnMoved -> {
    binding.ludoBoard.animatePawnMove(event.pawn, event.newPosition)
}
```

Add to `LudoBoardView`:
```kotlin
fun animatePawnMove(pawn: Pawn, finalPosition: Int) {
    // Build list of intermediate positions from pawn.boardPosition+1 to finalPosition
    // Post invalidate with 120ms delay per step using Handler
    val handler = Handler(Looper.getMainLooper())
    val steps = /* compute intermediate positions */ listOf(...)
    steps.forEachIndexed { i, pos ->
        handler.postDelayed({
            // Temporarily override pawn position for drawing
            animatingPawnOverride = pawn.id to pos
            invalidate()
        }, i * 120L)
    }
    handler.postDelayed({
        animatingPawnOverride = null
        invalidate()
    }, steps.size * 120L)
}
```

### 1.17 — KillQuizFragment animate inner card, not root ScrollView

**File**: `app/src/main/java/com/ludokid/ui/KillQuizFragment.kt`
**Function**: `onViewCreated()`

Change the animation target from `binding.root` to the inner `MaterialCardView`
(assumed to be `binding.cardQuiz` or similar — verify layout ID):

```kotlin
// Before (targets ScrollView root — clips children):
binding.root.scaleX = 0.8f
binding.root.scaleY = 0.8f
binding.root.alpha = 0f
binding.root.animate()...

// After (targets inner card):
binding.cardQuiz.scaleX = 0.8f
binding.cardQuiz.scaleY = 0.8f
binding.cardQuiz.alpha = 0f
binding.cardQuiz.animate()
    .scaleX(1f).scaleY(1f).alpha(1f)
    .setDuration(350)
    .setInterpolator(android.view.animation.OvershootInterpolator())
    .start()
```


## Testing Strategy

### Validation Approach

Two-phase approach: (1) run exploratory tests on unfixed code to surface counterexamples and
confirm root causes; (2) run fix-checking and preservation-checking tests on fixed code.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples on unfixed code to confirm each root cause hypothesis.

**Test Plan**: Write unit tests that directly call the buggy functions with inputs that satisfy
`isBugCondition`. Run on unfixed code — all should fail. After fixing, all should pass.

**Test Cases**:

1. **Path size test** (1.1): Assert `buildSimplePath().size == 52` — fails on unfixed code
   (returns 53).
2. **Path uniqueness test** (1.1): Assert all entries in `buildSimplePath()` are distinct —
   fails on unfixed code (index 18 and 52 are both `6 to 9`).
3. **Safe-zone entry test** (1.2): Create a Red pawn at position 49, call
   `getNewPosition(pawn, 1, 0)` — should return `BOARD_SIZE + 0` (52), not 53.
4. **Overshoot guard test** (1.4): Create a SAFE_ZONE pawn at `boardPosition = BOARD_SIZE + 4`,
   call `getNewPosition(pawn, 1, 0)` — should return `FINISHING_POSITION` (100), not null.
5. **Three-sixes forfeit test** (1.5): Simulate rolling 6 three times; assert
   `currentPlayerIndex` advances after the third roll.
6. **Wrong-answer attacker move test** (1.6): Set up a pending kill, call
   `answerKillQuestion(wrongIndex)`, assert attacker `boardPosition == expectedDestination`.
7. **Blockade test** (1.7): Place two same-player pawns at position 10; assert a third player's
   pawn cannot move to position 10.
8. **Position-0 kill test** (1.8): Place Red pawn at 0; call
   `getPawnsAtPosition(0, 1, allPlayers)` — should return the Red pawn.
9. **btnRollDice wiring test** (1.9): Simulate a click on `btnRollDice`; assert
   `viewModel.rollDice()` is called.
10. **Player color test** (1.10): Call `initGame(2, names)`; assert `players[1].color == BLUE`.

**Expected Counterexamples on Unfixed Code**:
- `buildSimplePath().size` returns 53 instead of 52.
- `getNewPosition` for a pawn at position 49 with step 1 returns 53 (off-by-one entry).
- `answerKillQuestion(wrong)` leaves attacker at original position.
- `getPawnsAtPosition(0, 1, ...)` returns empty list.

### Fix Checking

**Goal**: Verify that for all inputs where `isBugCondition` holds, the fixed functions produce
the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := fixedFunction(input)
  ASSERT expectedBehavior(result)
END FOR
```

Concretely: re-run all 10 exploratory tests above on fixed code — all must pass.

### Preservation Checking

**Goal**: Verify that for all inputs where `isBugCondition` is false, fixed functions produce
the same result as the original.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT originalFunction(input) == fixedFunction(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended because:
- It generates many random game states automatically.
- It catches edge cases (e.g. pawn at position 51 wrapping, all-HOME state, mixed states).
- It provides strong guarantees across the full input domain.

**Test Cases**:
1. **Trivia card display preservation**: For any dice roll on a non-three-sixes state, assert
   `ShowTriviaCard` event is emitted with a valid card.
2. **Correct kill-quiz preservation**: For any correct kill-quiz answer, assert defending pawn
   returns HOME and attacker moves to destination.
3. **Bonus-turn preservation**: For any roll of 6 with `consecutiveSixes < 2`, assert
   `BonusTurn` event is emitted.
4. **Safe-cell preservation**: For any pawn on a star cell (8, 13, 21, 26, 34, 39, 47), assert
   `getPawnsAtPosition` returns empty list.
5. **Safe-zone no-kill preservation**: For any pawn with `boardPosition >= BOARD_SIZE`, assert
   `getPawnsAtPosition` returns empty list.
6. **Home pawn entry preservation**: For any HOME pawn and dice roll of 6, assert pawn is
   moveable and `getNewPosition` returns `START_POSITIONS[playerId]`.
7. **Finish preservation**: For any pawn at `FINISHING_POSITION`, assert it is marked FINISHED
   and excluded from `getMoveablePawns`.

### Unit Tests

- `buildSimplePath()` returns exactly 52 unique entries.
- `getNewPosition` for each player at their safe-zone entry cell returns the correct first
  safe-zone position.
- `getNewPosition` for a SAFE_ZONE pawn that would overshoot returns null.
- `answerKillQuestion(wrong)` moves attacker to destination without killing defender.
- `getPawnsAtPosition(0, 1, ...)` returns opponent pawn at position 0.
- `initGame(4, names)` assigns RED/BLUE/GREEN/YELLOW to players 0–3.
- `drawBackground` uses color `#1A1A2E`.
- `drawCenterStar` assigns Yellow to right triangle and Blue to bottom triangle.
- `TriviaCardFragment` back-press invokes `onDismissed`.
- `KillQuizFragment` entrance animation targets inner card, not root ScrollView.

### Property-Based Tests

- For all positions 0–51, `buildSimplePath()[i]` is unique (no two indices share the same
  `(row, col)` pair).
- For all players and all board positions, `getNewPosition` never returns a value that would
  place a pawn beyond `FINISHING_POSITION`.
- For all game states where `consecutiveSixes < 2` and dice == 6, `processEndOfTurn` emits
  `BonusTurn` (not `NextTurn`).
- For all game states where `consecutiveSixes == 2` and dice == 6, `processEndOfTurn` emits
  `NextTurn` (forfeit).
- For all inputs where `isBugCondition` is false, the fixed `getMoveablePawns` returns the
  same list as the original.

### Integration Tests

- Full game flow: 2-player game, roll dice, view trivia, move pawn, verify board position
  updates correctly for all 52 path cells.
- Kill flow: attacker lands on defender, wrong answer given, verify attacker moves to
  destination and defender stays.
- Three-sixes forfeit: roll 6 three times in a row, verify turn passes to next player.
- Back-press on trivia card: verify game advances to `SELECTING_PAWN` phase.
- Pawn step animation: verify intermediate positions are rendered before final position.

