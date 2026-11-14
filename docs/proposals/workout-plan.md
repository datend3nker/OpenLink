# Proposal: the workout plan on the wearable (version 4)

Status: idea, not decided, no code. It extends delegated recording (version 2) and borrows the
settings model (version 3). Asked for by LiftLog. LiftLog runs a strength workout on the phone
and wants the watch to show it and take sets.

## What it does

The watch shows the workout the client runs: the exercise and set it is on, the target reps, the
weight, and the rest timer. It follows every change made on the phone. As a second step, the
user marks a set done on the watch (with the reps done, and maybe a changed weight), and the
client takes that over as if it had been entered on the phone. The client stays the owner: its
session is the truth, and the watch shows a copy and suggests changes.

Out of scope: building or storing workouts on the watch, a history ("last time: 8 × 60 kg"),
notes, links, and coaching. None of it crosses.

## Shape: a new capability on the `RECORD` session

A plan is not a session of its own. It belongs to a `RECORD` session the caller owns and shares
its session id, owner, lifetime and storage:

- **New capability `workout`**, with its own consent. It is the one place OpenLink carries user
  text (below), so the user is asked separately and can take it back without losing `recording`.
- **No session without `recording`.** A plan is taken only for a running `RECORD` session that
  the caller owns. A plan for any other session id is a no-op, as for any call under a missing
  capability. When the session ends, the plan is dropped. When the owner loses `workout`, the
  plan is dropped and the recording goes on.
- **Methods**, appended last:

```
// IOpenLinkHost, version 4
oneway void setWorkoutPlan(in OpenLinkWorkoutPlan plan);          // capability workout
// IOpenLinkListener, version 4, to the session's owner only
void onWorkoutResults(in OpenLinkWorkoutResults results);         // capability workout
```

An extension of `OpenLinkRecordingRequest` was considered and rejected. A plan changes many
times during a workout, but a start is sent once and a repeated start is dropped by design. It
would also put user text under the `recording` consent.

## The plan, client to watch: a full snapshot

`OpenLinkWorkoutPlan`:

- `sessionId`: the `RECORD` session.
- `revision`: a number the client increases with each change. The host keeps the newest and
  drops an older or equal one, so a replay after a reconnect is harmless.
- `exercises`, in order, each with:
  - `exerciseId`: chosen by the client, stable for the workout, 1 to 32 of `[A-Za-z0-9_-]`.
  - `label`: what the watch shows (see Text).
  - `kind`: `WEIGHTED` or `CARDIO`.
  - `resistance`: `NONE`, `EXTERNAL` or `BODYWEIGHT`.
  - `supersetWithNext`.
  - `segmentType`: the Health Connect exercise segment type, optional, a number.
  - `restSeconds` (`min`, `max`, `afterFailure`).
  - `sets`, in order.
- Each set has:
  - `setId`, with the same rule as `exerciseId`.
  - The target: `repsMin`..`repsMax`, or `seconds`, or `meters`.
  - `weight` and `weightUnit` (`KG`, `LB`), each optional.
  - The state as the client holds it: `done`, `repsDone`, `doneAtMillis`, and for cardio
    `secondsDone` and `metersDone`.
  - `updatedAtMillis`: when the client last changed this set.
- `currentSetId`: the set the client shows as next. Sets may be done out of order, so the watch
  is told and does not guess.

**Why a snapshot, not deltas.** The plan is small, and a snapshot can be neither missed nor
applied twice. The client sends the whole plan after every change, at most a few times a
second. Sets added or removed, an exercise swapped, a weight edited, a different order: all of
these are just the next snapshot. The watch computes the rest timer itself from the last
`doneAtMillis` and the rest time, so the plan does not need to change every second.

## The results, watch to client: last writer per set

The back-channel carries state, not events. That makes a replay idempotent with no event ids
and no acknowledgments, the same way settings work.

`OpenLinkWorkoutResults`: `sessionId`, and per `setId` the watch's latest word:

- `done` (`true`: set done, `false`: undone).
- `repsDone`, optionally `weight` and `weightUnit` (changed on the watch), and for cardio
  `secondsDone` and `metersDone`.
- `atMillis`: when the user did it, on the phone's clock (the host app converts).

The rules:

- **Newer wins, per set.** The client applies a result only if its `atMillis` is newer than
  the set's own `updatedAtMillis`, then sends the next snapshot, which echoes it. If the user
  changed the same set on both devices, the later change wins, as with settings. The watch
  treats its own change as pending until a snapshot carries it or a newer change replaces it.
- **The host holds the results for the session** (one entry per set, bounded by the plan) and
  sends all of them to the owner. It sends them on each change, after the start that a
  reconnecting owner replays (as `Already`), and with the session's end. A client that was dead
  or unbound when a set was ticked therefore still gets it, and getting it twice changes
  nothing.
- **Results go with the end.** The results are kept with the ended workout (one of the 16
  remembered) and sent after a replayed start answered with `Ended`. A set ticked just before a
  stop on the watch is not lost.
- A result for a `setId` that the current plan does not have (the client removed it meanwhile)
  is dropped by the client.
- Not in the first step: skipping an exercise, reordering, or adding sets on the watch. The
  watch can only mark sets in the plan done or undone. This keeps the client the only one that
  edits the plan's shape.

## Text: one bounded label, under its own consent

Exercise names are user text, and a fixed catalogue cannot name a user's own exercise. This is
therefore a deliberate, narrow exception to "no free text". It is confined so that it cannot
carry anything else:

- **One text field: `label`.** At most 48 characters (code points). The library on both sides
  strips control characters and line breaks and drops a plan whose label does not fit. Ids are
  opaque and restricted to `[A-Za-z0-9_-]`. Everything else is numbers and enums.
- **Shown, never stored as data.** The host app and the watch show the label and keep it only
  while the session runs (and in the host's session storage for a restart). It is deleted with
  the plan, never written to Health Connect, and never logged; logs count plans and sets.
- **Under `workout` only.** No other capability gains a text field.
- **Health Connect gets types, not names.** The optional `segmentType` (a number) maps an
  exercise to a Health Connect segment type. If there is none, the segment type is
  unknown.

## Versioning and devices

- **Protocol version 4.** A side that negotiated 3 or lower sends no plan and accepts none. The
  client library drops `setWorkoutPlan` for such a host, just as it fails `RECORD` against a
  version 1 host. The workout itself is unaffected: it records as before.
- **Device flags.** Two new values in `recordingFeatures`:
  - `WORKOUT_PLAN`: the device shows a plan.
  - `WORKOUT_INPUT`: the device takes sets done.

  A client reads them from `getDevices()` before it promises the user anything. A host app
  whose device has neither ignores the plan. No error is needed: the flags have already told
  the client.
- A plan is taken only while the session runs. Before the host app has reported `RECORDING`,
  the host holds the plan and the host app passes it on once the watch has started.

## Health Connect: whoever records, writes

The watch records, so the host writes, as with every `RECORD` session:

- the exercise session;
- heart rate;
- one `ExerciseSegment` per done set, with:
  - its `segmentType`;
  - `repetitions` = `repsDone`;
  - a time span from the end of the previous rest to `doneAtMillis`.

The host writes these from the final plan and results at the end. The client writes no session
and no segments of its own for that workout, just as LiftLog skips its session today. Weights,
targets and labels stay in the client's own database. Health Connect has no type for them, and
the label must not go there. `onDataWritten` tells the client when it is written.

## Limits

| What | Limit |
| --- | --- |
| exercises per plan | 40 |
| sets per exercise | 30 |
| sets per plan | 300 |
| `label` | 48 characters, no control characters or line breaks |
| `exerciseId`, `setId` | 1 to 32 of `[A-Za-z0-9_-]` |
| reps | 0 to 1 000 |
| weight | 0 to 1 000 (kg or lb) |
| seconds, meters | 0 to 86 400, 0 to 1 000 000 |
| plans per second | the host keeps the newest; the host app may coalesce to 2 per second |

A plan at the limit is about 30 KB as a `Bundle`, well within a binder transaction. A plan that
breaks a limit is dropped whole by the library on the side that receives it, and the drop is
counted in the logs.

**Never crosses:** notes, links, photos, a workout's history or previous performance, the
user's account or any id outside this session, and free text other than `label`.

## Restart and reconnect

- **Client restarts:** it replays its start, which `RECORD` already requires, then sends its
  current plan. The host answers with the results it holds.
- **Host restarts:** it reloads the plan and the results from its session storage, next to the
  workout, and the host app sends the plan to the watch again.
- **Watch reconnects:** the host app sends it the newest plan, and the watch sends the changes
  it still holds as pending.

## A second screen: other clients follow the same workout

Wanted: the user picks the UI. A workout started in LiftLog can also be followed, and sets
ticked, in OpenVitals, or the other way round. The host already holds the plan and the results,
so it can serve them to more than the owner. This is a step after the watch works; nothing above
changes for the owner.

- **One owner, as now.** The client that started the `RECORD` session owns the plan. Only it
  sends `setWorkoutPlan`, and only it can pause, resume or stop.
- **Followers.** Another client trusted with `workout` may follow a running workout: it calls
  `followWorkout(sessionId)` and from then on gets the plan with `onWorkoutPlan` (the newest
  snapshot at once, then each change) and the results the owner gets. It finds running workouts
  with `getWorkouts()`, which lists the session id, the device and the exercise type, no labels.
- **A follower marks sets like the watch does.** `setWorkoutResults(results)` from a follower
  goes into the same per-set results, newer wins. The owner applies it and echoes it in the next
  snapshot, so the watch and every follower show the same state. A follower cannot change the
  plan's shape.
- **Consent covers it.** The labels reach another app only if the user granted that app
  `workout`; the consent screen says that it may see and mark workouts started by other apps.
  Should that be a separate capability (`workout.follow`)? See the open questions.
- **Afterwards** every app sees the finished workout in Health Connect (the session, heart rate,
  a segment per set), with no OpenLink at all. Weights and labels are in the owner's database
  only: an app that wants them after the end must have followed the workout.

This breaks one rule on purpose: until now a session's events go to its owner alone. It is
limited to `workout`, to clients the user trusted with it, and to reading the plan and marking
sets.

## Open questions

- Is following a workout part of `workout`, or its own capability, so that an app may send its
  own plan without seeing other apps' workouts? Proposal: its own, `workout.follow`.

- Does `WORKOUT_INPUT` need its own consent toggle, or is `workout` enough? Proposal: one
  capability, with the device flag deciding what is possible.
- Should a set's start (the end of rest) cross explicitly, for more exact Health Connect
  segments? Proposal: not at first.
- Garmin: can a Connect IQ app show a plan of this size? That decides whether `WORKOUT_PLAN` is
  Wear OS only at first.
