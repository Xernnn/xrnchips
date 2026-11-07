# Status

Version 0.1.0, 6 November 2026.

## What has been tested

**Unit tests** (`./gradlew :app:testDebugUnitTest`, 32 tests, all passing).

- `EngineTest.sessionMatchesTheReference` replays an eight-hand session that was first played on
  the reference app (see below) with the same seats, stacks and moves. The stacks, bets, pots,
  turns, raise limits, shortcut amounts, blind levels and showdown lists it asserts were read off
  that app's screen, except at the two points where this app now does something else on purpose,
  which the test marks. It covers antes, a re-raise, a split pot with an odd chip, a player
  leaving, three buy-ins, a four-way all-in with two side pots, a one-chip all-in whose unmatched
  chips come back, heads-up play, a short all-in raise, adding chips after a fold, three blind
  increases, the end of the game and the final accounts.
- The other engine tests cover the raise rule (a short all-in, several that add up, a short bet
  after checks), pot limit, the big blind ante with a stack too short for it, buying back in and
  leaving when broke, a returning player's account, an eight-handed button move, splitting, folded
  chips in side pots, the minutes clock, the clock before the first deal, and blinds posted from a
  stack too small for them.
- `LedgerTest` covers settling up: that the payments square everyone, that they are as few as
  possible in a case where pairing the biggest loser with the biggest winner is not, 300 random
  sets of results, and money that still adds up after rounding.
- `SetupTest` covers the Play screen's field rules, including what switching between hands and
  minutes does to the lengths.

**By hand on an emulator** (Android 15, 1080 x 2400) with the debug build: the same session through
the real screens, plus the Play screen's fields and name dialog, every control of the raise dialog,
Manage (add chips refused and allowed, leave table, buy-in), Undo, Save, Load and resuming, Exit,
the winner dialog, an eight-player table, the minutes clock with pause and a level change, Back at
the table and in dialogs, holding versus tapping, hold time 0, and double tap. Settings, About and
Licenses were opened and looked at.

**The release build** (shrunk by R8, about 2 MB) on the same emulator, installed over the debug
build: it read the games the debug build had saved, resumed one, took a move, saved and listed it.
Also with this build: renaming and deleting a player, removing a level, deleting one save and all
saves, the felt picker and resetting the settings, the hand rankings, adding chips to a folded
player and undoing it, Restart, and Undo from the showdown.

**The table's motion** was checked from a screen recording of a call and a street change: the
stack and pot counting, the bets sliding into the pot and the card dropping onto the board.

**The ledger and the newer rules**, on the emulator with the debug build: Continue from the start
screen, the question before Reset, a three-way all-in after which one player bought back in for
less and one left, the results and the payments for that game, stacks in big blinds, the big blind
ante being posted, and the pot-limit cap in the raise dialog.

**On a phone** (Redmi Note 14 5G, Android 15): the app installs, starts and deals a hand.

## Not tested

- The sounds have not been listened to. They were only checked to be present, the right length and
  not clipped. That includes the two newest, for the blinds going up and for a new turn.
- Vibration.
- Screens narrower than 360 dp or with a large font scale.

## Where it differs from the reference

xrnchips is modelled on Pocket Poker Chips 3.0.0 for Android and follows its screens, flows and
betting rules. It was written from that app's behaviour alone, and shares no code, art, sound or
font with it. The differences:

**On purpose**

- A player out of chips is asked whether to buy back in or leave. The reference removes them
  without asking, so a rebuy there means typing the player in again.
- The app keeps accounts and settles up. The reference has nothing of the kind.
- The raise rule is the usual one: an all-in for less than a full raise does not change the
  minimum and does not let players who have already acted raise again. The reference treats it as
  a full raise on both counts.
- Antes go into the main pot as dead money. The reference counts them as part of each player's
  bet, which comes to the same thing while everyone posts the same ante.
- Pot limit and the big blind ante are choices on the Play screen that the reference does not
  have, as are showing stacks in big blinds and the sound for a new turn.
- The player to act is named above the action buttons, and the blinds going up has a sound.
- The start screen has Continue while an autosave exists, and Reset on the Play screen asks first.
  The reference resets at once.
- Its own name, icon, colours, typeface, drawings, sounds and wording.
- No ads, so no Remove Ads button and no Restore Purchase. About has no Feedback, Rate App or
  Privacy Policy links, as there is no store listing and nothing is collected.
- Heads-up, the dealer always posts the small blind. The reference does the same except in the hand
  after the small blind's player left a three-handed table, where it made the dealer the big blind.
- The Back button works: it closes dialogs, goes back a screen, and opens the menu at the table.
  The reference ignores it everywhere.
- Action buttons are rounded rectangles that fill from the left while held, rather than round
  buttons with a ring.
- The player to act is marked harder than in the reference: a thick glowing ring and a mint tint
  on the seat, a mint name, and a mint "Ann's turn" pill over the action buttons (asked for after
  a play test on 7 November 2026).
- The blinds, the next level and what is left of the level are in the middle of the table above the
  board, where the reference has them in a box in the top right corner.
- Switching between hands and minutes sets every level's length to 4 hands or 15 minutes, and that
  is also the length the first added level gets. The reference keeps the numbers when the unit
  changes and starts at 30.
- A live player has no card marker. The reference shows two cards above the seat; here a player
  who folded is dimmed instead.
- Touching a number field clears it and shows the old value faintly, where the reference selects
  the old value. Typing replaces it either way.
- A Duration of 0 or nothing is refused. The reference keeps it.
- The notice that the blinds went up does not block the table while it shows.
- The status bar stays visible.

**Not known to match** (the reference was not probed for these)

- Whether its Undo takes back add chips, leave table and buy-in, and whether a loaded game can
  still be undone. Here both are true.
- Whether its Delete all removes the autosave. Here it does.
- How many blind levels it allows. Here 20.
- Its minimum raise after an all-in that added less than a big blind. Here the minimum is the size
  of the last bet or raise whatever it was, as the reference showed for a larger short all-in.
- Its default hold time, vibration and table colour on a fresh install. The values here are the
  ones found on a phone that had been in use.
- Whether a double tap replaces holding or adds to it. Here it adds, and holding kept working on
  the reference with double tap on.
