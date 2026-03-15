# Bugfix Requirements Document

## Introduction

LudoKid is a 2–4 player Ludo game for Android with an integrated trivia mechanic. The app
suffers from three categories of defects: (1) broken game logic — incorrect board path mapping,
wrong safe-zone entry points, missing three-sixes forfeit rule, unenforced blockades, and a
wrong-answer kill flow that skips the attacker's move entirely; (2) visual problems — the board
background clashes with the dark theme, home-stretch coloring is misassigned, the center finish
triangles map colors to wrong directions, and the Roll Dice button click is never wired up;
(3) animation defects — the dice scale-down animator is not reversed before the bounce-up
leaving the die at 85 % scale, the TriviaCardFragment back-press path never fires `onDismissed`
leaving the game frozen, and pawn movement has no step-by-step cell animation.
The trivia fact display and kill-quiz trivia flow must be preserved exactly as-is.

## Bug Analysis

### Current Behavior (Defect)


1.1 WHEN `buildSimplePath()` is called THEN the system returns 53 entries with board position 18 (`6 to 9`) duplicated at index 52, causing every pawn at positions ≥ 18 to render at the wrong cell and position arithmetic to wrap incorrectly via `position % path.size`

1.2 WHEN a pawn belonging to Green (player 1), Yellow (player 2), or Blue (player 3) approaches its safe-zone entry THEN the system uses wrong `SAFE_ZONE_ENTRY` values (Green=11, Yellow=24, Blue=37 are off by the correct offsets), causing pawns to enter the safe zone one or two cells too early or too late

1.3 WHEN `getMoveablePawns` evaluates a pawn in `PawnState.SAFE_ZONE` THEN the system computes remaining steps as `SAFE_ZONE_SIZE - safePos` where `safePos` is derived with an incorrect offset, returning wrong moveable-pawn lists and allowing pawns to overshoot the finish

1.4 WHEN `getNewPosition` is called for a `SAFE_ZONE` pawn and `newSafePos == SAFE_ZONE_SIZE` THEN the system returns `FINISHING_POSITION` correctly, but WHEN `newSafePos > SAFE_ZONE_SIZE` the guard uses `>` instead of `>=`, so a pawn exactly at the boundary is not blocked and can overshoot

1.5 WHEN a player rolls 6 three consecutive times THEN the system grants a third bonus turn instead of forfeiting the turn, because `consecutiveSixes` in `GameState` is never incremented or checked

1.6 WHEN a player selects a pawn that would land on an opponent and then answers the kill quiz incorrectly THEN the system skips the attacker's move entirely (the attacker stays in place) instead of moving the attacker to the destination without killing

1.7 WHEN `isBlockade` detects two same-player pawns on a cell THEN the system does not prevent opponent pawns from passing through or landing on that cell, because the blockade check result is never used in `getMoveablePawns` or `getNewPosition`

1.8 WHEN `getPawnsAtPosition` is called for position 0 (Red's start cell) THEN the system returns an empty list because position 0 is in `SAFE_CELLS`, making it impossible to kill any pawn sitting on position 0 even when it belongs to an opponent

1.9 WHEN `GameActivity.setupDiceView` wires the click listener THEN the system attaches the roll action only to `diceView` (the canvas view) and never to `btnRollDice` (the separate Material button), so tapping the Roll Dice button does nothing

1.10 WHEN `MainActivity` assigns player names THEN the system labels them Red/Blue/Green/Yellow in that order, but `PlayerColor.values()` is indexed Red/Green/Yellow/Blue, so Player 2 is assigned GREEN color while the UI labels them "Blue Player"


1.11 WHEN `LudoBoardView.drawBackground` fills the board THEN the system uses color `#F5F5DC` (beige/cream), which clashes with the app's dark theme (`#0F0F1A` background) and makes the board look visually inconsistent

1.12 WHEN `LudoBoardView.getPathColor` colors the home-stretch lanes THEN the system checks `row in 7..7 && col in 1..5` for Red's lane, but Red's actual home stretch in the path runs along column 7 rows 8–13 (downward), so the wrong cells are colored and the correct cells remain white

1.13 WHEN `LudoBoardView.drawCenterStar` draws the four finish triangles THEN the system assigns Green to the top triangle and Yellow to the bottom triangle, but standard Ludo convention (and the board's quadrant layout) requires Red at the left, Green at the top, Yellow at the right, and Blue at the bottom — the current mapping places Yellow and Blue in swapped positions

1.14 WHEN `DiceView.animateRoll` is called THEN the system starts a `scaleDown` animator (1f → 0.85f, 600 ms) and simultaneously starts the frame-flip runnable after 50 ms; the bounce-up animator (`scaleX` 0.85f → 1.1f → 1f) starts from 0.85f only if the scale-down has completed, but because the runnable finishes at ~690 ms and the scale-down ends at 600 ms, the dice is left at 0.85f scale during the final frame display before the bounce fires

1.15 WHEN `TriviaCardFragment` is dismissed via the Android back button THEN the system calls `dismiss()` without invoking `onDismissed`, leaving `GameState.phase` stuck at `SHOWING_TRIVIA` and the game frozen with no way to proceed

1.16 WHEN a pawn is moved after `GameEvent.PawnMoved` is emitted THEN the system calls `binding.ludoBoard.invalidate()` which redraws the pawn instantly at the new position with no intermediate animation, making movement feel abrupt and unpolished

1.17 WHEN `KillQuizFragment` animates its entrance THEN the system applies `OvershootInterpolator` scale animation to the root `ScrollView`, causing the overshoot to clip child content against the ScrollView bounds and produce a visual glitch


### Expected Behavior (Correct)

2.1 WHEN `buildSimplePath()` is called THEN the system SHALL return exactly 52 unique entries covering all board positions 0–51 with no duplicates, so that `positionToGrid[n]` maps each logical position to the correct grid cell

2.2 WHEN a pawn belonging to any player approaches its safe-zone entry THEN the system SHALL use corrected `SAFE_ZONE_ENTRY` values (Red=50, Green=11, Yellow=24, Blue=37 adjusted to match the 52-cell path layout) so that pawns enter the colored home stretch at the correct cell

2.3 WHEN `getMoveablePawns` evaluates a `SAFE_ZONE` pawn THEN the system SHALL compute `safePos = pawn.boardPosition - (BOARD_SIZE + playerId * SAFE_ZONE_SIZE)` correctly and return the pawn as moveable only when `diceValue <= (SAFE_ZONE_SIZE - safePos)`

2.4 WHEN `getNewPosition` is called for a `SAFE_ZONE` pawn THEN the system SHALL use `newSafePos >= SAFE_ZONE_SIZE` (not `>`) as the overshoot guard, returning `null` for any move that would exceed the finish cell

2.5 WHEN a player rolls 6 three consecutive times in a row THEN the system SHALL forfeit that player's turn, reset `consecutiveSixes` to 0, and advance to the next player's turn

2.6 WHEN a player answers the kill quiz incorrectly THEN the system SHALL move the attacking pawn to the destination cell without killing the defending pawn, so the attacker still advances its position

2.7 WHEN `getMoveablePawns` or `getNewPosition` evaluates a move THEN the system SHALL check `isBlockade` at the destination and SHALL NOT allow a pawn to land on or pass through a cell occupied by two or more pawns of the same opponent

2.8 WHEN `getPawnsAtPosition` is called for any board position THEN the system SHALL only treat a cell as safe (no-kill) when it is a designated star/safe cell that is NOT a player's own start position being occupied by an opponent, ensuring kills are possible at all non-safe cells

2.9 WHEN the user taps the Roll Dice button (`btnRollDice`) THEN the system SHALL trigger the same roll action as tapping the `diceView` canvas, so both interaction targets work correctly

2.10 WHEN `MainActivity` assigns player colors THEN the system SHALL map Player 1 → RED, Player 2 → BLUE, Player 3 → GREEN, Player 4 → YELLOW to match the UI labels and standard Ludo color order


2.11 WHEN `LudoBoardView.drawBackground` fills the board THEN the system SHALL use a dark-neutral color (e.g. `#1A1A2E`) that is consistent with the app's dark theme

2.12 WHEN `LudoBoardView.getPathColor` colors the home-stretch lanes THEN the system SHALL color the correct cells: Red lane = col 7 rows 8–13, Green lane = col 7 rows 1–6, Yellow lane = col 7 rows 8–13 (right side), Blue lane = row 7 cols 9–13, matching the actual path layout

2.13 WHEN `LudoBoardView.drawCenterStar` draws the four finish triangles THEN the system SHALL assign Red to the left triangle, Green to the top triangle, Yellow to the right triangle, and Blue to the bottom triangle, matching standard Ludo board convention

2.14 WHEN `DiceView.animateRoll` is called THEN the system SHALL ensure the scale-down animation completes (or is cancelled) before the frame-flip runnable begins, and the bounce-up animation SHALL start from the actual current scale value so the dice is never left at a partial scale

2.15 WHEN `TriviaCardFragment` is dismissed via the back button THEN the system SHALL invoke `onDismissed` so that `GameEngine.onTriviaDismissed()` is called and the game advances from `SHOWING_TRIVIA` to `SELECTING_PAWN`

2.16 WHEN a pawn moves to a new board position THEN the system SHALL animate the pawn stepping through each intermediate cell at a consistent speed (e.g. ~120 ms per cell) before snapping to the final position, giving visible step-by-step movement

2.17 WHEN `KillQuizFragment` animates its entrance THEN the system SHALL apply the scale/overshoot animation to the inner `MaterialCardView` (not the root `ScrollView`) so that child content is never clipped during the animation

### Unchanged Behavior (Regression Prevention)

3.1 WHEN a player rolls the dice THEN the system SHALL CONTINUE TO fetch and display a trivia fact card before the pawn selection phase

3.2 WHEN a pawn lands on an opponent's pawn at a non-safe cell THEN the system SHALL CONTINUE TO trigger the kill-quiz flow requiring the attacker to answer a multiple-choice trivia question

3.3 WHEN the kill quiz answer is correct THEN the system SHALL CONTINUE TO send the defending pawn back to its home position

3.4 WHEN a player rolls a 6 (and has not rolled three consecutive sixes) THEN the system SHALL CONTINUE TO grant that player a bonus turn after their move

3.5 WHEN a pawn is in `PawnState.HOME` and the player rolls a 6 THEN the system SHALL CONTINUE TO allow that pawn to enter the board at the player's start position

3.6 WHEN a pawn reaches `FINISHING_POSITION` THEN the system SHALL CONTINUE TO mark it as `PawnState.FINISHED` and exclude it from future move selection

3.7 WHEN all four pawns of a player reach `PawnState.FINISHED` THEN the system SHALL CONTINUE TO trigger the game-over flow and display the winner

3.8 WHEN a pawn is on a safe cell (star) THEN the system SHALL CONTINUE TO prevent it from being killed by an opponent

3.9 WHEN a pawn enters the colored home stretch (safe zone) THEN the system SHALL CONTINUE TO prevent it from being killed by opponents

3.10 WHEN no pawns are moveable for the current player THEN the system SHALL CONTINUE TO skip that player's turn and advance to the next player

3.11 WHEN the trivia card is displayed THEN the system SHALL CONTINUE TO auto-dismiss after 8 seconds if the player does not tap "Got it"

3.12 WHEN the kill quiz timer expires THEN the system SHALL CONTINUE TO treat the timeout as a wrong answer, keeping the defending pawn safe

