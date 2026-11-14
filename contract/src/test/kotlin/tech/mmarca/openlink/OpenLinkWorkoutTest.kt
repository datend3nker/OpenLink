package tech.mmarca.openlink

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpenLinkWorkoutTest {

    private fun set(id: String) = OpenLinkWorkoutSet(id, repsMin = 8, repsMax = 12, weight = 60.0, weightUnit = OpenLinkWeightUnit.KG)

    private fun plan(vararg exercises: OpenLinkWorkoutExercise, current: String? = null) =
        OpenLinkWorkoutPlan("w", exercises.toList(), current)

    private fun exercise(id: String, label: String = "Bench press", vararg sets: OpenLinkWorkoutSet) =
        OpenLinkWorkoutExercise(id, label, sets = sets.toList())

    @Test
    fun `a plan within the limits crosses as it is`() {
        val plan = plan(exercise("e1", "Bench press", set("s1"), set("s2")), exercise("e2", "Kniebeuge 🏋", set("s3")), current = "s2")

        assertThat(plan.valid()).isEqualTo(plan)
    }

    @Test
    fun `a label loses its control characters and line breaks`() {
        val plan = plan(exercise("e1", " Bench\npress\u0007\t"))

        assertThat(plan.valid()?.exercises?.single()?.label).isEqualTo("Benchpress")
    }

    @Test
    fun `a plan that breaks a limit is dropped whole`() {
        assertThat(plan(exercise("e1", "x".repeat(OpenLinkWorkoutPlan.MAX_LABEL_LENGTH + 1))).valid()).isNull()
        assertThat(plan(exercise("e1", "\n")).valid()).isNull() // no label left
        assertThat(plan(exercise("bad id")).valid()).isNull()
        assertThat(plan(exercise("e1"), exercise("e1")).valid()).isNull() // an id twice
        assertThat(plan(exercise("e1", "a", set("s1")), exercise("e2", "b", set("s1"))).valid()).isNull()
        assertThat(plan(exercise("e1", "a", set("s1")), current = "s9").valid()).isNull() // no such set
        assertThat(plan(exercise("e1", "a", set("s1").copy(weight = Double.NaN))).valid()).isNull()
        assertThat(plan(exercise("e1", "a", set("s1").copy(repsMin = 12, repsMax = 8))).valid()).isNull()
        assertThat(plan(exercise("e1", "a", set("s1").copy(repsDone = -1))).valid()).isNull()
        val many = (0..OpenLinkWorkoutPlan.MAX_SETS_PER_EXERCISE).map { set("s$it") }.toTypedArray()
        assertThat(plan(exercise("e1", "a", *many)).valid()).isNull()
        val exercises = (0..OpenLinkWorkoutPlan.MAX_EXERCISES).map { exercise("e$it") }.toTypedArray()
        assertThat(plan(*exercises).valid()).isNull()
        assertThat(OpenLinkWorkoutPlan("", emptyList()).valid()).isNull()
    }

    @Test
    fun `the newer word on a set wins, set by set`() {
        val watch = OpenLinkWorkoutResults(
            "w",
            mapOf("s1" to OpenLinkSetResult(done = true, atMillis = 10, repsDone = 8), "s2" to OpenLinkSetResult(done = true, atMillis = 20)),
        )
        val later = OpenLinkWorkoutResults(
            "w",
            mapOf("s1" to OpenLinkSetResult(done = false, atMillis = 30), "s2" to OpenLinkSetResult(done = false, atMillis = 20), "s3" to OpenLinkSetResult(done = true, atMillis = 5)),
        )

        val merged = watch.merge(later)

        assertThat(merged.sets["s1"]?.done).isFalse()
        assertThat(merged.sets["s2"]?.done).isTrue() // a tie keeps what was there
        assertThat(merged.sets.keys).containsExactly("s1", "s2", "s3")
        assertThat(merged.merge(later)).isEqualTo(merged) // twice changes nothing
    }

    @Test
    fun `a result without a time, with a bad id or out of range does not cross`() {
        val results = OpenLinkWorkoutResults(
            "w",
            mapOf(
                "ok" to OpenLinkSetResult(done = true, atMillis = 10, repsDone = 8, weight = 62.5),
                "no time" to OpenLinkSetResult(done = true, atMillis = 10),
                "s2" to OpenLinkSetResult(done = true, atMillis = 0),
                "s3" to OpenLinkSetResult(done = true, atMillis = 10, repsDone = 5_000),
                "s4" to OpenLinkSetResult(done = true, atMillis = 10, weight = -1.0),
            ),
        )

        assertThat(results.valid().sets.keys).containsExactly("ok")
    }
}
