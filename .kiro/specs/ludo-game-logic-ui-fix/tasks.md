# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Bug Condition** - 17-Defect Exploration Suite
  - **CRITICAL**: This test MUST FAIL on unfixed code — failure confirms the bugs exist
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: These tests encode the expected behavior — they will validate the fix when they pass after implementation
  - **GOAL**: Surface counterexamples that demonstrate each of the 17 bugs
  - **Scoped PBT Approach**: For deterministic bugs, scope each property to the concrete failing case(s) to ensure reproducibility
  - Test 1.1 — path size: assert `buildSimplePath().size == 52` (fails: returns 53, duplicate `6 to 9` at index 52)
  - Test 1.2 — safe-zone entry: create Red pawn at position 49, call `getNewPosition(pawn, 1, 0)`, assert result == 52 (BOARD_SIZE + 0); fails because `+1` in stepsToEntry causes entry one step late
  - Test 1.3/1.4 — overshoot guard: create SAFE_ZONE pawn at `boardPosition = BOARD_SIZE + 4`, call `getNewPosition(pawn, 1, 0)`, assert result == FINISHING_POSITION (100); also assert `getNewPosition(pawn, 2, 0) == null`
  - Test 1.5 — three-sixes forfeit: simulate rolling 6 three times with `consecutiveSixes` tracking; assert `currentPlayerIndex` advances after the third roll (fails: bonus turn granted instead)
  - Test 1.6 — wrong-answer attacker move: set up PendingKill, call `answerKillQuestion(wrongIndex)`, assert attacker `boardPosition == expectedDestination` (fails: attacker stays in place)
  - Test 1.7 — blockade enforcement: place two same-player pawns at position 10; assert a third player's pawn at position 7 with dice=3 is NOT in `getMoveablePawns` result (fails: blockade not checked)
  - Test 1.8 — position-0 kill eligibility: place Red pawn at position 0; call `getPawnsAtPosition(0, 1, allPlayers)`, assert result contains Red's pawn (fails: returns empty because 0 is in SAFE_CELLS)
  - Test 1.9 — btnRollDice wiring: in GameActivity, assert `btnRollDice` has a click listener that triggers `viewModel.rollDice()` when phase == WAITING_TO_ROLL (fails: no listener attached)
  - Test 1.10 — player color assignment: call `initGame(2, names)`, assert `players[1].color == PlayerColor.BLUE` (fails: assigned GREEN via `values()[1]`)
  - Test 1.11 — board background color: assert `drawBackground` uses `#1A1A2E` (fails: uses `#F5F5DC`)
  - Test 1.12 — home-stretch lane coloring: assert `getPathColor(7, 3)` returns `lightRed` and `getPathColor(3, 7)` returns `lightGreen` (fails: wrong ranges)
  - Test 1.13 — center triangle colors: assert right triangle uses `colorYellow` and bottom triangle uses `colorBlue` (fails: swapped)
  - Test 1.14 — dice scale sequencing: assert dice `scaleX == 1f` after `animateRoll` completes (fails: left at 0.85f)
  - Test 1.15 — back-press fires onDismissed: call `onCancel` on TriviaCardFragment, assert `onDismissed` was invoked (fails: no `onCancel` override)
  - Test 1.16 — step animation: assert `animatePawnMove` posts intermediate positions before final (fails: only `invalidate()` called)
  - Test 1.17 — KillQuizFragment animation target: assert entrance animation is applied to inner card, not `binding.root` ScrollView (fails: targets root)
  - Run all tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct — it proves the bugs exist)
  - Document counterexamples found to understand root causes
  - Mark task complete when tests are written, run, and failures are documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11, 1.12, 1.13, 1.14, 1.15, 1.16, 1.17_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Trivia & Core Game Behavior
  - **IMPORTANT**: Follow observation-first methodology — run unfixed code with non-buggy inputs first
  - Observe: rolling dice on a fresh state (consecutiveSixes=0) emits `ShowTriviaCard` with a valid card
  - Observe: correct kill-quiz answer sends defending pawn HOME and moves attacker to destination
  - Observe: rolling 6 with `consecutiveSixes < 2` emits `BonusTurn` event
  - Observe: `getPawnsAtPosition` returns empty list for star cells 8, 13, 21, 26, 34, 39, 47
  - Observe: `getPawnsAtPosition` returns empty list for any position >= BOARD_SIZE (safe zone)
  - Observe: HOME pawn with dice=6 is included in `getMoveablePawns` and `getNewPosition` returns `START_POSITIONS[playerId]`
  - Observe: pawn at FINISHING_POSITION is marked FINISHED and excluded from `getMoveablePawns`
  - Observe: no moveable pawns → `NoMoveablePawns` event emitted, turn advances
  - Observe: trivia card auto-dismisses after 8 seconds (timer fires `dismissAndNotify`)
  - Observe: kill-quiz timer expiry calls `handleAnswer(-1, ...)` treating it as wrong answer
  - Write property-based tests asserting all observed behaviors hold for all non-buggy inputs
  - Verify all preservation tests PASS on UNFIXED code before proceeding
  - **EXPECTED OUTCOME**: Tests PASS (confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10, 3.11, 3.12_

- [x] 3. Fix game logic defects (LudoBoard.kt, GameEngine.kt, GameState.kt)

  - [x] 3.1 Remove duplicate path entry in `buildSimplePath()` (LudoBoardView.kt)
    - Delete the stray trailing `6 to 9` entry so the list has exactly 52 elements
    - The list must end with `7 to 0, 6 to 0` (positions 50–51)
    - _Bug_Condition: isBugCondition(input) where buildSimplePath().size != 52_
    - _Expected_Behavior: buildSimplePath().size == 52 AND all entries are distinct_
    - _Preservation: positionToGrid mapping for positions 0–17 must remain unchanged_
    - _Requirements: 2.1_

  - [x] 3.2 Fix `stepsToEntry` off-by-one in `getNewPosition()` (LudoBoard.kt)
    - Change `((safeEntry - currentPos + BOARD_SIZE) % BOARD_SIZE) + 1` to `(safeEntry - currentPos + BOARD_SIZE) % BOARD_SIZE`
    - Verify Red pawn at position 49 with step=1 returns 52 (BOARD_SIZE + 0)
    - _Bug_Condition: isBugCondition(input) where pawn.state==ACTIVE AND steps==stepsToEntry_
    - _Expected_Behavior: getNewPosition returns BOARD_SIZE + playerId * SAFE_ZONE_SIZE on exact entry_
    - _Preservation: All non-entry moves (steps < stepsToEntry) must still wrap correctly via % BOARD_SIZE_
    - _Requirements: 2.2_

  - [x] 3.3 Fix safe-zone overshoot guard in `getNewPosition()` and `getMoveablePawns()` (LudoBoard.kt)
    - In `getNewPosition` SAFE_ZONE branch: reorder guard so `newSafePos == SAFE_ZONE_SIZE` returns FINISHING_POSITION first, then `newSafePos > SAFE_ZONE_SIZE` returns null
    - In `getMoveablePawns` SAFE_ZONE branch: confirm `remainingSteps = SAFE_ZONE_SIZE - safePos` uses the same offset formula as `getNewPosition`
    - _Bug_Condition: isBugCondition(input) where pawn.state==SAFE_ZONE AND newSafePos >= SAFE_ZONE_SIZE_
    - _Expected_Behavior: newSafePos == SAFE_ZONE_SIZE → FINISHING_POSITION; newSafePos > SAFE_ZONE_SIZE → null_
    - _Preservation: SAFE_ZONE pawns with valid moves (newSafePos < SAFE_ZONE_SIZE) must still be moveable_
    - _Requirements: 2.3, 2.4_

  - [x] 3.4 Implement three-consecutive-sixes forfeit (GameEngine.kt)
    - In `rollDice()`: compute `newConsecutiveSixes = if (diceValue == 6) state.consecutiveSixes + 1 else 0` and include in the new GameState copy
    - In `processEndOfTurn()`: before granting bonus turn, check `if (state.consecutiveSixes >= 3)` → call `nextTurn()` and return
    - _Bug_Condition: isBugCondition(input) where diceValue==6 AND consecutiveSixes==2_
    - _Expected_Behavior: turn forfeited, consecutiveSixes reset to 0, next player's turn begins_
    - _Preservation: consecutiveSixes < 2 with dice==6 must still emit BonusTurn (requirement 3.4)_
    - _Requirements: 2.5_

  - [x] 3.5 Fix wrong-answer kill flow to still move attacker (GameEngine.kt)
    - In `answerKillQuestion()` else branch, replace `processEndOfTurn(skipState)` with: compute `newPosition` via `LudoBoard.getNewPosition`, call `movePawnInState`, post `PawnMoved` event, delay 500ms, then call `processEndOfTurn`
    - _Bug_Condition: isBugCondition(input) where killQuizAnswer.isCorrect==false_
    - _Expected_Behavior: attacker.boardPosition == newPosition after call; defender.state unchanged_
    - _Preservation: correct-answer path (kills defender, moves attacker) must remain identical (requirement 3.3)_
    - _Requirements: 2.6_

  - [x] 3.6 Enforce blockade in `getMoveablePawns()` (LudoBoard.kt)
    - In `getMoveablePawns` ACTIVE branch, add `&& !isBlockade(newPos, allPlayers)` after the `newPos != null` check
    - _Bug_Condition: isBugCondition(input) where isBlockade(destination, allPlayers)==true_
    - _Expected_Behavior: pawn NOT included in getMoveablePawns when destination is blockaded_
    - _Preservation: moves to non-blockaded cells must still be allowed_
    - _Requirements: 2.7_

  - [x] 3.7 Remove position 0 from SAFE_CELLS (LudoBoard.kt)
    - Change `val SAFE_CELLS = setOf(0, 8, 13, 21, 26, 34, 39, 47)` to `setOf(8, 13, 21, 26, 34, 39, 47)`
    - _Bug_Condition: isBugCondition(input) where position==0 AND excludePlayerId!=0_
    - _Expected_Behavior: getPawnsAtPosition(0, 1, allPlayers) returns Red pawn when present_
    - _Preservation: all other star cells (8, 13, 21, 26, 34, 39, 47) must still prevent kills (requirement 3.8)_
    - _Requirements: 2.8_

  - [x] 3.8 Wire `btnRollDice` click listener in `GameActivity.setupDiceView()` (GameActivity.kt)
    - Add `binding.btnRollDice.setOnClickListener { if (state?.phase == GamePhase.WAITING_TO_ROLL) viewModel.rollDice() }` after the existing `diceView` listener
    - _Bug_Condition: isBugCondition(input) where uiEvent.source=="btnRollDice"_
    - _Expected_Behavior: tapping btnRollDice triggers rollDice() identically to tapping diceView_
    - _Preservation: existing diceView click listener must remain unchanged_
    - _Requirements: 2.9_

  - [x] 3.9 Fix player color assignment in `GameEngine.initGame()` (GameEngine.kt)
    - Replace `color = PlayerColor.values()[index]` with explicit mapping: 0→RED, 1→BLUE, 2→GREEN, 3→YELLOW
    - _Bug_Condition: isBugCondition(input) where initGame.playerCount>=2 AND players[1].color!=BLUE_
    - _Expected_Behavior: players[0]=RED, players[1]=BLUE, players[2]=GREEN, players[3]=YELLOW_
    - _Preservation: player IDs, names, and pawn initialization must remain unchanged_
    - _Requirements: 2.10_

- [x] 4. Fix UI rendering defects (LudoBoardView.kt)

  - [x] 4.1 Set dark board background color (LudoBoardView.kt)
    - In `drawBackground()`, change `Color.parseColor("#F5F5DC")` to `Color.parseColor("#1A1A2E")`
    - _Bug_Condition: isBugCondition(input) where drawBackground uses "#F5F5DC"_
    - _Expected_Behavior: board background renders as #1A1A2E, consistent with dark theme_
    - _Preservation: all other draw calls (home zones, path, pawns) must be unaffected_
    - _Requirements: 2.11_

  - [x] 4.2 Correct home-stretch lane coloring in `getPathColor()` (LudoBoardView.kt)
    - Update range checks to: `row == 7 && col in 1..5` → lightRed; `row == 7 && col in 9..13` → lightBlue; `col == 7 && row in 1..5` → lightGreen; `col == 7 && row in 9..13` → lightYellow
    - Verify these ranges match the cells drawn by `drawSafeZones` exactly
    - _Bug_Condition: isBugCondition(input) where getPathColor returns wrong color for safe-zone cells_
    - _Expected_Behavior: each home-stretch lane cell is colored with its player's light color_
    - _Preservation: non-lane path cells must still return colorWhite_
    - _Requirements: 2.12_

  - [x] 4.3 Fix center triangle color assignment in `drawCenterStar()` (LudoBoardView.kt)
    - Swap right triangle from `colorBlue` to `colorYellow`; swap bottom triangle from `colorYellow` to `colorBlue`
    - Update `getCenterFinishPosition`: player 2 (Yellow) → `PointF(cx + offset, cy)` (right); player 3 (Blue) → `PointF(cx, cy + offset)` (bottom)
    - _Bug_Condition: isBugCondition(input) where drawCenterStar assigns colorYellow to bottom triangle_
    - _Expected_Behavior: Red=left, Green=top, Yellow=right, Blue=bottom_
    - _Preservation: Red (left) and Green (top) triangles must remain unchanged_
    - _Requirements: 2.13_

- [x] 5. Fix animation defects (DiceView.kt, TriviaCardFragment.kt, GameActivity.kt, KillQuizFragment.kt)

  - [x] 5.1 Fix dice scale animation sequencing (DiceView.kt)
    - Add an `AnimatorListenerAdapter.onAnimationEnd` to the shake `AnimatorSet` that starts the frame-flip runnable only after the set completes
    - Remove the standalone `postDelayed(frameRunnable, 50)` call that runs concurrently
    - In the frameRunnable's final branch, read `scaleX` at runtime instead of hardcoding `0.85f`
    - _Bug_Condition: isBugCondition(input) where animateRoll called AND scaleDown not complete when bounceUp starts_
    - _Expected_Behavior: dice scaleX == 1f after animateRoll completes; no partial-scale frames visible_
    - _Preservation: shake animation, frame-flip randomization (8 frames × 80ms), and onComplete callback must still fire_
    - _Requirements: 2.14_

  - [x] 5.2 Override `onCancel` in `TriviaCardFragment` to call `onDismissed` (TriviaCardFragment.kt)
    - Add `override fun onCancel(dialog: android.content.DialogInterface) { super.onCancel(dialog); countDownTimer?.cancel(); onDismissed?.invoke() }`
    - _Bug_Condition: isBugCondition(input) where backPress AND fragment==TriviaCardFragment_
    - _Expected_Behavior: back-press invokes onDismissed, GameEngine.onTriviaDismissed() called, phase advances to SELECTING_PAWN_
    - _Preservation: btnGotIt tap path (dismissAndNotify) and auto-dismiss timer must remain unchanged (requirements 3.11)_
    - _Requirements: 2.15_

  - [x] 5.3 Implement step-by-step pawn movement animation (LudoBoardView.kt + GameActivity.kt)
    - Add `animatingPawnOverride: Pair<Int, Int>? = null` field to LudoBoardView (pawnId to tempPosition)
    - Add `fun animatePawnMove(pawn: Pawn, finalPosition: Int)` to LudoBoardView: build list of intermediate board positions from `pawn.boardPosition + 1` to `finalPosition`, post `invalidate()` with 120ms delay per step via Handler, clear override and invalidate on completion
    - In `getPawnCenter`, check `animatingPawnOverride` first and use the override position when the pawn ID matches
    - In GameActivity `observeEvents`, replace `binding.ludoBoard.invalidate()` with `binding.ludoBoard.animatePawnMove(event.pawn, event.newPosition)` for `GameEvent.PawnMoved`
    - _Bug_Condition: isBugCondition(input) where GameEvent.PawnMoved received_
    - _Expected_Behavior: pawn renders at each intermediate cell for ~120ms before reaching finalPosition_
    - _Preservation: final board state after animation must match the position set by movePawnInState; all other pawn draw logic unchanged_
    - _Requirements: 2.16_

  - [x] 5.4 Animate inner card in `KillQuizFragment`, not root ScrollView (KillQuizFragment.kt)
    - Change animation target from `binding.root` to `binding.cardQuiz` (verify layout ID in `fragment_kill_quiz.xml`)
    - Apply `scaleX = 0.8f`, `scaleY = 0.8f`, `alpha = 0f` to `binding.cardQuiz` and animate to `scaleX(1f).scaleY(1f).alpha(1f)` with OvershootInterpolator
    - _Bug_Condition: isBugCondition(input) where animationTarget==binding.root (ScrollView)_
    - _Expected_Behavior: overshoot animation plays on inner MaterialCardView; no child content clipping_
    - _Preservation: animation duration (350ms), interpolator, and quiz content display must remain unchanged_
    - _Requirements: 2.17_

- [x] 6. Verify bug condition exploration test now passes

  - [x] 6.1 Re-run bug condition exploration tests from task 1
    - **Property 1: Expected Behavior** - 17-Defect Exploration Suite
    - **IMPORTANT**: Re-run the SAME tests from task 1 — do NOT write new tests
    - The tests from task 1 encode the expected behavior for all 17 defects
    - When these tests pass, it confirms all expected behaviors are satisfied
    - Run all 17 bug condition tests on FIXED code
    - **EXPECTED OUTCOME**: All tests PASS (confirms all 17 bugs are fixed)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12, 2.13, 2.14, 2.15, 2.16, 2.17_

  - [x] 6.2 Verify preservation tests still pass
    - **Property 2: Preservation** - Trivia & Core Game Behavior
    - **IMPORTANT**: Re-run the SAME tests from task 2 — do NOT write new tests
    - Run all preservation property tests on FIXED code
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions in trivia/core behavior)
    - Confirm all 12 preservation behaviors (3.1–3.12) still hold after all fixes

- [x] 7. Checkpoint — Ensure all tests pass
  - Ensure all tests pass; ask the user if questions arise
  - Confirm the full test suite (exploration + preservation) is green
  - Do a final visual check: dark board background, correct lane colors, correct center triangles, smooth dice animation, step-by-step pawn movement, kill-quiz card animation
