package tech.mmarca.openlink

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

enum class OpenLinkExerciseKind { WEIGHTED, CARDIO }

enum class OpenLinkResistance { NONE, EXTERNAL, BODYWEIGHT }

enum class OpenLinkWeightUnit { KG, LB }

/**
 * One set of a workout plan: its target, and its state as the client holds it. [setId] is the
 * client's, stable for the workout. [updatedAtMillis] is when the client last changed the set:
 * a result from the wearable counts only if it is newer.
 */
data class OpenLinkWorkoutSet(
    val setId: String,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val targetSeconds: Int? = null,
    val targetMeters: Int? = null,
    val weight: Double? = null,
    val weightUnit: OpenLinkWeightUnit? = null,
    val done: Boolean = false,
    val repsDone: Int? = null,
    val secondsDone: Int? = null,
    val metersDone: Int? = null,
    val doneAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
) {
    internal fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_ID, setId)
        putNullableInt(KEY_REPS_MIN, repsMin)
        putNullableInt(KEY_REPS_MAX, repsMax)
        putNullableInt(KEY_TARGET_SECONDS, targetSeconds)
        putNullableInt(KEY_TARGET_METERS, targetMeters)
        putNullableDouble(KEY_WEIGHT, weight)
        if (weightUnit != null) putString(KEY_WEIGHT_UNIT, weightUnit.name)
        putBoolean(KEY_DONE, done)
        putNullableInt(KEY_REPS_DONE, repsDone)
        putNullableInt(KEY_SECONDS_DONE, secondsDone)
        putNullableInt(KEY_METERS_DONE, metersDone)
        putLong(KEY_DONE_AT, doneAtMillis)
        putLong(KEY_UPDATED_AT, updatedAtMillis)
    }

    internal fun fits(): Boolean =
        OpenLinkWorkoutPlan.isId(setId) &&
            listOf(repsMin, repsMax, repsDone).all { it == null || it in 0..OpenLinkWorkoutPlan.MAX_REPS } &&
            (repsMin == null || repsMax == null || repsMin <= repsMax) &&
            listOf(targetSeconds, secondsDone).all { it == null || it in 0..OpenLinkWorkoutPlan.MAX_SECONDS } &&
            listOf(targetMeters, metersDone).all { it == null || it in 0..OpenLinkWorkoutPlan.MAX_METERS } &&
            OpenLinkWorkoutPlan.isWeight(weight)

    internal companion object {
        const val KEY_ID = "setId"
        const val KEY_REPS_MIN = "repsMin"
        const val KEY_REPS_MAX = "repsMax"
        const val KEY_TARGET_SECONDS = "targetSeconds"
        const val KEY_TARGET_METERS = "targetMeters"
        const val KEY_WEIGHT = "weight"
        const val KEY_WEIGHT_UNIT = "weightUnit"
        const val KEY_DONE = "done"
        const val KEY_REPS_DONE = "repsDone"
        const val KEY_SECONDS_DONE = "secondsDone"
        const val KEY_METERS_DONE = "metersDone"
        const val KEY_DONE_AT = "doneAtMillis"
        const val KEY_UPDATED_AT = "updatedAtMillis"

        fun fromBundle(bundle: Bundle) = OpenLinkWorkoutSet(
            setId = bundle.getString(KEY_ID) ?: "",
            repsMin = bundle.getNullableInt(KEY_REPS_MIN),
            repsMax = bundle.getNullableInt(KEY_REPS_MAX),
            targetSeconds = bundle.getNullableInt(KEY_TARGET_SECONDS),
            targetMeters = bundle.getNullableInt(KEY_TARGET_METERS),
            weight = bundle.getNullableDouble(KEY_WEIGHT),
            weightUnit = bundle.getEnumOrNull<OpenLinkWeightUnit>(KEY_WEIGHT_UNIT),
            done = bundle.getBoolean(KEY_DONE, false),
            repsDone = bundle.getNullableInt(KEY_REPS_DONE),
            secondsDone = bundle.getNullableInt(KEY_SECONDS_DONE),
            metersDone = bundle.getNullableInt(KEY_METERS_DONE),
            doneAtMillis = bundle.getLong(KEY_DONE_AT, 0L),
            updatedAtMillis = bundle.getLong(KEY_UPDATED_AT, 0L),
        )
    }
}

/**
 * One exercise of a workout plan. [label] is what the wearable shows: the one text OpenLink
 * carries, user text, at most [OpenLinkWorkoutPlan.MAX_LABEL_LENGTH] characters. It is shown
 * and kept only while the workout runs: never logged, never written to Health Connect, which
 * gets [segmentType] (a Health Connect exercise segment type) instead.
 */
data class OpenLinkWorkoutExercise(
    val exerciseId: String,
    val label: String,
    val kind: OpenLinkExerciseKind = OpenLinkExerciseKind.WEIGHTED,
    val resistance: OpenLinkResistance = OpenLinkResistance.NONE,
    val supersetWithNext: Boolean = false,
    val segmentType: Int? = null,
    val restMinSeconds: Int? = null,
    val restMaxSeconds: Int? = null,
    val restAfterFailureSeconds: Int? = null,
    val sets: List<OpenLinkWorkoutSet> = emptyList(),
) {
    internal fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_ID, exerciseId)
        putString(KEY_LABEL, label)
        putString(KEY_KIND, kind.name)
        putString(KEY_RESISTANCE, resistance.name)
        putBoolean(KEY_SUPERSET, supersetWithNext)
        putNullableInt(KEY_SEGMENT_TYPE, segmentType)
        putNullableInt(KEY_REST_MIN, restMinSeconds)
        putNullableInt(KEY_REST_MAX, restMaxSeconds)
        putNullableInt(KEY_REST_FAILURE, restAfterFailureSeconds)
        putParcelableArrayList(KEY_SETS, ArrayList(sets.map { it.toBundle() }))
    }

    internal companion object {
        const val KEY_ID = "exerciseId"
        const val KEY_LABEL = "label"
        const val KEY_KIND = "kind"
        const val KEY_RESISTANCE = "resistance"
        const val KEY_SUPERSET = "supersetWithNext"
        const val KEY_SEGMENT_TYPE = "segmentType"
        const val KEY_REST_MIN = "restMinSeconds"
        const val KEY_REST_MAX = "restMaxSeconds"
        const val KEY_REST_FAILURE = "restAfterFailureSeconds"
        const val KEY_SETS = "sets"

        fun fromBundle(bundle: Bundle) = OpenLinkWorkoutExercise(
            exerciseId = bundle.getString(KEY_ID) ?: "",
            label = bundle.getString(KEY_LABEL) ?: "",
            kind = bundle.getEnumOrNull<OpenLinkExerciseKind>(KEY_KIND) ?: OpenLinkExerciseKind.WEIGHTED,
            resistance = bundle.getEnumOrNull<OpenLinkResistance>(KEY_RESISTANCE) ?: OpenLinkResistance.NONE,
            supersetWithNext = bundle.getBoolean(KEY_SUPERSET, false),
            segmentType = bundle.getNullableInt(KEY_SEGMENT_TYPE),
            restMinSeconds = bundle.getNullableInt(KEY_REST_MIN),
            restMaxSeconds = bundle.getNullableInt(KEY_REST_MAX),
            restAfterFailureSeconds = bundle.getNullableInt(KEY_REST_FAILURE),
            sets = bundle.bundles(KEY_SETS).map(OpenLinkWorkoutSet::fromBundle),
        )
    }
}

/**
 * The workout a client runs, for the wearable that records it to show (version 4, capability
 * `workout`): always the whole plan, sent again after every change. It belongs to the `RECORD`
 * session [sessionId] of the client that sends it. The client library stamps [revision]; the
 * host keeps the newest. [currentSetId] is the set the client shows as next.
 *
 * What crosses the binder is checked with [valid] on both sides before anything uses it.
 */
data class OpenLinkWorkoutPlan(
    val sessionId: String,
    val exercises: List<OpenLinkWorkoutExercise> = emptyList(),
    val currentSetId: String? = null,
    val revision: Long = 0L,
) : Parcelable {
    /**
     * This plan as it may cross, labels without control characters and line breaks, or null if
     * it breaks a limit: such a plan is dropped whole, since half a plan would show a wrong
     * workout.
     */
    fun valid(): OpenLinkWorkoutPlan? {
        val cleaned = exercises.map { it.copy(label = it.label.filterNot(Char::isISOControl).trim()) }
        val sets = cleaned.flatMap { it.sets }
        val fits = sessionId.isNotBlank() &&
            cleaned.size <= MAX_EXERCISES && sets.size <= MAX_SETS &&
            cleaned.map { it.exerciseId }.distinct().size == cleaned.size &&
            sets.map { it.setId }.distinct().size == sets.size &&
            (currentSetId == null || sets.any { it.setId == currentSetId }) &&
            cleaned.all { exercise ->
                isId(exercise.exerciseId) &&
                    exercise.label.isNotEmpty() &&
                    exercise.label.codePointCount(0, exercise.label.length) <= MAX_LABEL_LENGTH &&
                    exercise.sets.size <= MAX_SETS_PER_EXERCISE &&
                    listOf(exercise.restMinSeconds, exercise.restMaxSeconds, exercise.restAfterFailureSeconds)
                        .all { it == null || it in 0..MAX_SECONDS } &&
                    exercise.sets.all { it.fits() }
            }
        return if (fits) copy(exercises = cleaned) else null
    }

    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_SESSION, sessionId)
        putParcelableArrayList(KEY_EXERCISES, ArrayList(exercises.map { it.toBundle() }))
        if (currentSetId != null) putString(KEY_CURRENT, currentSetId)
        putLong(KEY_REVISION, revision)
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeFields(toBundle())

    companion object {
        private const val KEY_SESSION = "sessionId"
        private const val KEY_EXERCISES = "exercises"
        private const val KEY_CURRENT = "currentSetId"
        private const val KEY_REVISION = "revision"

        const val MAX_EXERCISES = 40
        const val MAX_SETS_PER_EXERCISE = 30
        const val MAX_SETS = 300
        const val MAX_LABEL_LENGTH = 48
        const val MAX_ID_LENGTH = 32
        const val MAX_REPS = 1_000
        const val MAX_WEIGHT = 1_000.0
        const val MAX_SECONDS = 86_400
        const val MAX_METERS = 1_000_000

        /** An exercise or set id: the client's, opaque, 1 to 32 of `[A-Za-z0-9_-]`. */
        fun isId(id: String): Boolean =
            id.length in 1..MAX_ID_LENGTH && id.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }

        internal fun isWeight(weight: Double?): Boolean = weight == null || (weight.isFinite() && weight in 0.0..MAX_WEIGHT)

        fun fromBundle(bundle: Bundle) = OpenLinkWorkoutPlan(
            sessionId = bundle.getString(KEY_SESSION) ?: "",
            exercises = bundle.bundles(KEY_EXERCISES).map(OpenLinkWorkoutExercise::fromBundle),
            currentSetId = bundle.getString(KEY_CURRENT),
            revision = bundle.getLong(KEY_REVISION, 0L),
        )

        @JvmField
        val CREATOR: Parcelable.Creator<OpenLinkWorkoutPlan> = parcelableCreator(::fromBundle) { arrayOfNulls(it) }
    }
}

/**
 * What the user did with one set on the wearable: marked it done ([done]) or not done again,
 * with what they did, at [atMillis] on the phone's clock.
 */
data class OpenLinkSetResult(
    val done: Boolean,
    val atMillis: Long,
    val repsDone: Int? = null,
    val weight: Double? = null,
    val weightUnit: OpenLinkWeightUnit? = null,
    val secondsDone: Int? = null,
    val metersDone: Int? = null,
) {
    internal fun fits(): Boolean =
        atMillis > 0L &&
            (repsDone == null || repsDone in 0..OpenLinkWorkoutPlan.MAX_REPS) &&
            (secondsDone == null || secondsDone in 0..OpenLinkWorkoutPlan.MAX_SECONDS) &&
            (metersDone == null || metersDone in 0..OpenLinkWorkoutPlan.MAX_METERS) &&
            OpenLinkWorkoutPlan.isWeight(weight)
}

/**
 * The wearable's latest word on the sets of the workout [sessionId], by set id (version 4,
 * capability `workout`). State, not events: the newer word on a set wins ([merge]), so getting
 * it twice changes nothing. The client applies a set's result if it is newer than its own
 * change of that set, and sends its plan again.
 */
data class OpenLinkWorkoutResults(
    val sessionId: String,
    val sets: Map<String, OpenLinkSetResult> = emptyMap(),
) : Parcelable {
    /** Only what may cross: at most [OpenLinkWorkoutPlan.MAX_SETS] results that fit. */
    fun valid(): OpenLinkWorkoutResults =
        copy(sets = sets.filter { (id, result) -> OpenLinkWorkoutPlan.isId(id) && result.fits() }.entries.take(OpenLinkWorkoutPlan.MAX_SETS).associate { it.toPair() })

    /** These results with every set of [other] that is newer there; a tie keeps this side's. */
    fun merge(other: OpenLinkWorkoutResults): OpenLinkWorkoutResults {
        val merged = sets.toMutableMap()
        for ((id, result) in other.sets) {
            val mine = merged[id]
            if (mine == null || result.atMillis > mine.atMillis) merged[id] = result
        }
        return copy(sets = merged)
    }

    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_SESSION, sessionId)
        putBundle(
            KEY_SETS,
            Bundle().apply {
                for ((id, result) in sets) {
                    putBundle(
                        id,
                        Bundle().apply {
                            putBoolean(KEY_DONE, result.done)
                            putLong(KEY_AT, result.atMillis)
                            putNullableInt(KEY_REPS_DONE, result.repsDone)
                            putNullableDouble(KEY_WEIGHT, result.weight)
                            if (result.weightUnit != null) putString(KEY_WEIGHT_UNIT, result.weightUnit.name)
                            putNullableInt(KEY_SECONDS_DONE, result.secondsDone)
                            putNullableInt(KEY_METERS_DONE, result.metersDone)
                        },
                    )
                }
            },
        )
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = dest.writeFields(toBundle())

    companion object {
        private const val KEY_SESSION = "sessionId"
        private const val KEY_SETS = "sets"
        private const val KEY_DONE = "done"
        private const val KEY_AT = "atMillis"
        private const val KEY_REPS_DONE = "repsDone"
        private const val KEY_WEIGHT = "weight"
        private const val KEY_WEIGHT_UNIT = "weightUnit"
        private const val KEY_SECONDS_DONE = "secondsDone"
        private const val KEY_METERS_DONE = "metersDone"

        fun fromBundle(bundle: Bundle): OpenLinkWorkoutResults {
            val sets = bundle.getBundle(KEY_SETS) ?: Bundle()
            return OpenLinkWorkoutResults(
                sessionId = bundle.getString(KEY_SESSION) ?: "",
                sets = sets.keySet().mapNotNull { id ->
                    val entry = sets.getBundle(id) ?: return@mapNotNull null
                    id to OpenLinkSetResult(
                        done = entry.getBoolean(KEY_DONE, false),
                        atMillis = entry.getLong(KEY_AT, 0L),
                        repsDone = entry.getNullableInt(KEY_REPS_DONE),
                        weight = entry.getNullableDouble(KEY_WEIGHT),
                        weightUnit = entry.getEnumOrNull<OpenLinkWeightUnit>(KEY_WEIGHT_UNIT),
                        secondsDone = entry.getNullableInt(KEY_SECONDS_DONE),
                        metersDone = entry.getNullableInt(KEY_METERS_DONE),
                    )
                }.toMap(),
            )
        }

        @JvmField
        val CREATOR: Parcelable.Creator<OpenLinkWorkoutResults> = parcelableCreator(::fromBundle) { arrayOfNulls(it) }
    }
}

/** The bundles under [key]; none if absent. Bundle is itself a Parcelable. */
@Suppress("DEPRECATION")
private fun Bundle.bundles(key: String): List<Bundle> = getParcelableArrayList<Bundle>(key).orEmpty()
