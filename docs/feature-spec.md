# xrnchips feature spec

What every screen does and how a hand is run. The rules live in
`app/src/main/java/com/xern/xrnchips/domain`; the numbers in the examples are asserted in its tests.

## Terms

- **Seat**: one of eight places around the table, numbered clockwise from the top: 0 top, 1 to 3
  down the right side, 4 bottom, 5 to 7 up the left side. Turn order, the button and the blinds all
  move clockwise through the occupied seats.
- **Stack**: a player's chips not yet bet.
- **Level**: one step of the blind structure: big blind, small blind, ante and how long it lasts.
- **Live**: dealt into the hand and not folded. **All in**: live with no chips left.

Amounts are whole chips, shown with thousands separators (`1,037`).

## Start screen

The icon, the name, and four buttons: **Play** (the Play screen), **Load**, **Settings**, **About**.
While a game is waiting in the autosave there is a fifth on top, **Continue**, which goes straight
back to its table.

## Play screen

Sets up a game. Everything on it is saved as it is changed and is there again next time.

**Starting chips.** Every player's stack at the first deal. 1 to 999,999, default 1,000.

**Blind structure.** A list of levels, each with Big, Small, Ante and Duration.

- **Add level** appends a level with double the last one's blinds and ante. The level that was last
  now needs a length: it takes the length of the level before it, or the usual length for the unit
  if it was the only one. Up to 20 levels.
- The red cross removes a level. The only level cannot be removed.
- **Hands / Minutes** says what Duration counts. Minutes is the default. The usual length is 4
  hands or 15 minutes, and switching the unit sets every level to it: a number of minutes means
  nothing as a number of hands.
- The last level never ends and shows ∞ instead of a length.
- The default structure is a single level of 10/5 with no ante.
- **Ante paid by**: *Everyone* (the default) or *Big blind*, who then posts the level's ante once
  for the whole table.
- **Bet limit**: *No limit* (the default) or *Pot limit*, where a bet or raise may not be more than
  the pot.

**Field rules.** A field is checked when it is left: Done, closing the keyboard, moving to another
field or leaving the screen. Touching a field clears it and leaves the old value showing faintly;
leaving without typing keeps the old value.

| Field | Range | Emptied or 0 | Also |
| --- | --- | --- | --- |
| Starting chips | 1 to 999,999 | keeps the old value | |
| Big | 1 to 999,999 | keeps the old value | set under Small: Small comes down to it |
| Small | 1 to 999,999 | keeps the old value | set over Big: Big goes up to it |
| Ante | 0 to 999,999 | becomes 0 | may be larger than the big blind |
| Duration | 1 to 999 | keeps the old value | |

Longer input stops at six digits (three for Duration).

**Players.** Eight seats around a small table. Tapping an empty seat asks for a name and has
**Add**. Tapping a player shows the name with **Update** (enabled once the name changes) and
**Delete**. A name is 1 to 8 characters and unique without regard to case; a clash shows
"That name is taken." and disables the button.

**Start** needs two players. With fewer it is dimmed and says "Add at least two players." or
"Add one more player." when pressed.

**Reset** (top right) asks first, then puts the screen back to the defaults: 1,000 chips, one
level of 10/5, Minutes, everyone antes, no limit, no players. It is dimmed when there is nothing to
reset.

## The table

- **Top bar**: the Menu button and the street (Preflop, Flop, Turn, River; Showdown while winners
  are picked).
- **Seats**: each player's name, stack, and a tag with the blind they posted (SB, BB) or their last
  move on this street (Check, Call, Bet, Raise, Fold). The stack is in chips, or in big blinds
  (`49.5 BB`) when that is switched on in Settings. The player to act has a thick mint ring with a
  soft glow inside it that breathes, a mint-tinted seat and a mint name. A folded player is dimmed, and so, less, is one waiting for the next hand. The dealer has
  a D on the corner of the seat. A player who has asked to leave has a walking figure by the name.
- **Bets**: the chips a player has put in on this street, in front of the seat. Before the flop the
  ante is counted in with it.
- **Middle**, from top to bottom, where the whole table can read it:
  - the blinds in play as `big/small`, the ante if there is one, the next level, and what is left
    of this one: `3 HANDS`, `1 HAND`, a `m:ss` countdown, or `NEXT HAND` when the level is used up
    and the blinds rise with the next deal. Tapping it pauses or resumes the countdown. At the last
    level it only shows the blinds;
  - five places for the board. It shows card backs: three from the flop, four from the turn, five
    from the river;
  - the pot: every chip in the middle, bets of the running street included.
- **Action buttons** for the player to act, under a mint pill saying whose turn it is (`Dan's turn`):
  - left: **Check** when there is nothing to call, **Call** with the amount, or **All in** with the
    stack when it does not cover the call;
  - middle: **Bet** when nobody has bet on the street, otherwise **Raise**. It opens the raise
    dialog. It is missing when the stack does not cover more than the call, or when the player
    may not raise (see Moves);
  - right: **Fold**.

  Check, Call, All in and Fold have to be held for the time set in Settings (0.4 s by default): a
  shade fills the button and it fires when full. With the time at 0 they fire on touch. With Double
  tap on, two quick taps fire them too.
- The screen stays on while the table is open.

While the table plays out a street change or a payout the buttons are gone and Menu is dimmed.

**Motion.** What changes is shown moving, so the table can follow it: a bet pops out in front of
the player and slides into the pot when the street closes; stacks and the pot count to their new
value, and a stack that wins swells for a moment; board cards drop onto their places, the three of
the flop one after another; the dealer button travels to the next dealer; the action buttons rise
into place for each new turn; dialogs and screens fade in.

## A hand

**Dealer.** Before the first hand a dialog shows the players at their seats: tap who deals, or
**Randomize**, which picks one and says who. After every hand the button moves to the next occupied
seat clockwise.

**Out of chips.** A hand that leaves someone with nothing stops the game for them to decide. Each
such player is listed with an amount (the starting stack by default) and two buttons: **Rebuy**
puts them back in with that many chips, **Leave** gives up the seat. The next hand is dealt once
all of them have answered. The undo arrow takes back the choice of winners.

**Before the deal**, in this order: players who are leaving lose their seats and the chips they
take with them are booked; players who bought in during the last hand are in; the blinds rise if
the level is used up (a notice "Blinds ↑ big / small" shows for two seconds, with a sound).

**Posting.** Everyone posts the ante, or with *Big blind* chosen only the big blind does. The
player after the dealer posts the small blind and the next the big blind. With two players the
dealer posts the small blind. A player who cannot cover an ante or blind posts what they have, the
ante first, and is all in.

**Turn order.** Before the flop the player after the big blind acts first (with two players: the
dealer). After the flop the first live player after the dealer who is not all in acts first. The
turn passes clockwise to the next live player who is not all in and still owes an action: one who
has not acted since the last bet or raise, or has put in less than the bet.

**Moves.**

- *Check*: nothing to call.
- *Call*: puts in the difference to the bet, or the whole stack if that is less (tagged Call).
- *Bet / Raise*: the amount entered is what goes in **on top of the call**. The smallest is the
  big blind on a street nobody has bet on, afterwards the size of the last full bet or raise. The
  largest is the stack left after calling, which is all in, or with *Pot limit* the pot as it
  stands once the call is in.
- An all-in for less than the minimum still raises the bet, but it is not a full raise: the
  minimum stays where it was, and a player who has already acted on the street may only call it
  or fold. They may raise again once the bet has gone up by a full raise since they acted, in
  one step or several. Players who have not acted yet are not restricted.

  > Blinds 10/5. A raise to 40 makes the minimum raise 30. The next player is all in for 60, 20
  > more. Those still to act may raise, by at least 30. When it comes back to the first raiser
  > they owe 20 and may only call or fold. Had a third player then gone all in for 75, the first
  > raiser would be facing 35 more than their 40 and could raise again.
- *Fold*: out of the hand. Chips already put in stay in the pot.

The big blind acts last before the flop when nobody raised, and may check or raise.

**End of a betting round.** When nobody owes an action:

- one live player left: the pot is theirs at once, and the next hand is dealt;
- on the river: the showdown;
- otherwise the bets go to the middle and the next street is dealt. If fewer than two live players
  can still bet, the remaining streets are dealt straight away, without betting, to the showdown.

**Pots.** At the showdown the chips are divided into a main pot and side pots by what each player
bet over the whole hand. A pot can be won by the live players who paid into it in full. Chips of
folded players stay in the pots. Chips nobody matched come back as a pot only their owner can win.
Antes are not bets: they all go into the main pot, whoever posted them.

> Blinds 10/5 with an ante of 2. Four players are all in before the flop for 500, 928, 1,039 and
> 1,039 in total. Main pot 2,000 (all four), side pot 1,284 (the three larger stacks), side pot 222
> (the two largest).

**Showdown dialog.** For each pot, the players who can win it, shortest contribution first: tap
the winner, or several to split it. A pot with one name is already chosen. **Next hand** pays out
once every pot has a winner. A split is in equal shares; chips that do not divide go one each to the
winners nearest the dealer's left (223 between two: 112 to the one nearer the dealer's left, 111 to
the other). The undo arrow takes back the last move; the (i) opens the hand rankings.

**Blind levels.** With Hands, a level lasts that many deals. With Minutes, a countdown runs while a
hand is on the table; it can be paused. Either way the blinds only change between hands.

**Game over.** When fewer than two players with chips are staying, the game ends: "*name* wins!"
with their chips, **Results**, **Restart** and **Exit**, and an undo arrow for a wrong answer
before it.

## Raise dialog

- The amount, large. Tap it to type one; while typing the other controls wait. A typed amount is
  brought into the allowed range.
- A slider from the minimum to all in, in steps of a big or small blind (Settings).
- **−** and **+** change the amount by a big blind, a small blind or 1 (Settings).
- **Min** and **Max**. With *Pot limit* the most is the pot, which may be less than the stack.
- Shortcuts. Before the flop while nobody has raised: **2BB**, **2.5BB**, **3BB**, that many big
  blinds on top of the call. Otherwise **1/3**, **1/2**, **3/4**, **Pot**: that share of the pot as
  it stands once the call is in, rounded half up. A shortcut outside the allowed range is dimmed.
- The button reads **Bet**, **Raise**, or **All in** when the whole stack goes in.
- When there is something to call: "Total: 133 (incl. 30 call)".
- When there is only one size to choose, because the stack cannot cover a full raise, the amount
  is fixed and the controls are off.

## Menu

Opened from the table. (i) opens the hand rankings; the cross closes it.

- **Undo** asks first, then takes back the last move: a player's action, the choice of winners, or
  a change made under Manage. It goes back across streets and hands, up to 200 moves, and is dimmed
  when there is nothing to take back.
- **Manage**: the seats. See below.
- **Results**: who is up and who is down. See below.
- **Save** keeps the game under Load and says "Game saved."
- **Settings** opens the Settings screen; Back returns to the table.
- **Restart** asks first, then starts over with the players, stacks and blinds the game began with.
- **Exit** asks first, then returns to the start screen. The game stays under Load as the autosave.

## Manage

The eight seats with the players in them.

- **A player**: their name, an amount (the starting stack by default) with **Add chips**, and
  **Leave table**.
  - Chips can be added once the player has folded or is not in the hand. Otherwise the button is
    dimmed and says "*name* has to fold before chips can be added."
  - Leave table marks the player. They finish the hand and their seat empties before the next
    deal. It cannot be taken back except with Undo, and their seat in Manage then only says they
    are leaving. Two players have to stay: otherwise it says so and does nothing.
- **An empty seat**: a name and an amount with **Buy-in**. The player sits down at once and is
  dealt in from the next hand. Names follow the same rules as on the Play screen.

## Results

The app keeps an account for everyone who has sat down: the chips they bought (the starting stack,
chips added, rebuys and later buy-ins) and the chips they left with. The account stays when they
leave, and is theirs again if they come back under the same name.

Results lists them, best first, with **Paid in**, **Has** (their stack, or what they left with)
and **Result**, the difference. Players who have left are dimmed. The results always add up to
zero. While a hand is being played the chips in its pot still count as their owners'.

**Settle up.** Enter what the starting stack costs and the results are turned into money, with a
list of who pays whom. The list is as short as it can be: wherever some of the players are square
among themselves they settle separately, which saves a payment. (With more than sixteen people
owing or owed, it stops looking for such groups and pairs the largest debts with the largest
winnings.) Amounts are rounded to whole
units, and what the rounding leaves over goes to the largest result. The price is remembered for
the next game. At 0 the game is for chips only.

> 1,000 chips cost 100. Ann paid in 1,000 and has 3,000; Bob paid 1,000 and left with nothing; Cat
> paid 1,500 and has 500. Bob pays Ann 100 and Cat pays Ann 100.

A game saved before there was a ledger starts one when it is opened: everyone is taken to have
paid for the chips they hold.

## Hand rankings

The ten hands from royal flush to high card, each with an example and a line of explanation. The
cards that make the hand are bright; the rest are faded.

## Load

The autosave first, then the saved games from newest to oldest. Each shows when it was saved, the
street, pot, blinds, ante if any, hand number, and every player's stack. **Play** opens it at the
table where it stopped, with its Undo history. **Delete** asks first. The bin (top right) deletes
all of them, autosave included, after asking.

The autosave is rewritten after every move and when the app goes to the background.

## Settings

| Setting | Values | Default |
| --- | --- | --- |
| Vibration: a light buzz on button presses | on, off | on |
| Double tap: check, call and fold also fire on a double tap | on, off | off |
| Sound | on, off | on |
| Turn sound: a soft note when the turn passes to the next player | on, off | off |
| Stacks in big blinds: stacks as a number of big blinds, not chips | on, off | off |
| Long press duration: seconds an action button has to be held | 0.0 to 2.0 in steps of 0.1 | 0.4 |
| Raise slider step size | big blind, small blind | big blind |
| Raise +/− buttons amount | big blind, small blind, 1 | big blind |
| Table style: the colour of the felt | green, blue, red, slate | green |

The arrow in a circle (top right) puts them all back to the defaults, the price from Settle up
included.

## About

The version, what the app is, and **Licenses**: the typeface and the open-source parts.

## Sound and vibration

One short sound each for a check, chips going in (bet, call, raise), a fold, the flop, a turn or
river card, a pot being paid, the blinds going up, and the end of the game. With Turn sound on
there is also a soft note each time the turn moves on. All are synthesised by
`tools/make_sounds.py`.

## The Back button

Back closes the dialog on top, or goes back one screen. At the table it opens the menu. Dialogs
that need an answer (dealer, showdown, out of chips, winner) ignore it.

## Storage

JSON files in the app's private folder: `settings.json`, `setup.json` (the Play screen), and
`saves/` with `autosave.json` and one file per saved game. Each saved game holds the table, the
accounts and the last 40 moves for Undo. Nothing leaves the phone; the app asks for no permissions.
