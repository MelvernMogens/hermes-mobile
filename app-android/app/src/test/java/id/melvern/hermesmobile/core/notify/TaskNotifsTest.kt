package id.melvern.hermesmobile.core.notify

import id.melvern.hermesmobile.core.repo.BotTask
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskNotifsTest {
    private val now = 1_000_000.0
    private fun t(id: String, status: String, startedAgo: Double = 600.0, lastAgo: Double = 300.0) =
        BotTask("coder", id, "Fix login", now - startedAgo, null, 5, now - lastAgo, status, "All good")

    private fun ids(l: List<BotTask>) = l.map { it.id }

    @Test fun running_to_done_notifies_once() {
        val prev = mapOf("a" to "running")
        val cur = listOf(t("a", "done"))
        assertEquals(listOf("a"), ids(TaskNotifs.decideTaskNotifs(prev, cur, emptySet(), now)))
        assertEquals(emptyList<String>(), ids(TaskNotifs.decideTaskNotifs(prev, cur, setOf("a"), now)))
    }

    @Test fun running_to_failed_or_stopped_notifies() {
        val prev = mapOf("a" to "running", "b" to "running")
        val cur = listOf(t("a", "failed"), t("b", "stopped", lastAgo = 5.0))
        assertEquals(listOf("a", "b"), ids(TaskNotifs.decideTaskNotifs(prev, cur, emptySet(), now)))
    }

    @Test fun still_running_never_notifies() {
        assertEquals(emptyList<String>(), ids(TaskNotifs.decideTaskNotifs(mapOf("a" to "running"), listOf(t("a", "running")), emptySet(), now)))
    }

    @Test fun first_seen_done_recent_notifies_but_old_does_not() {
        val cur = listOf(t("fresh", "done", startedAgo = 3600.0), t("old", "done", startedAgo = 3 * 3600.0))
        assertEquals(listOf("fresh"), ids(TaskNotifs.decideTaskNotifs(emptyMap(), cur, emptySet(), now)))
    }

    @Test fun old_task_seen_running_still_notifies_on_finish() {
        // tugas panjang (>2 jam) yang memang diamati jalan → tetap dinotif saat selesai
        val cur = listOf(t("long", "done", startedAgo = 5 * 3600.0))
        assertEquals(listOf("long"), ids(TaskNotifs.decideTaskNotifs(mapOf("long" to "running"), cur, emptySet(), now)))
    }

    @Test fun first_seen_stopped_waits_until_quiet() {
        val busy = listOf(t("p", "stopped", lastAgo = 10.0))
        assertEquals(emptyList<String>(), ids(TaskNotifs.decideTaskNotifs(emptyMap(), busy, emptySet(), now)))
        val quiet = listOf(t("p", "stopped", lastAgo = 600.0))
        assertEquals(listOf("p"), ids(TaskNotifs.decideTaskNotifs(mapOf("p" to "stopped"), quiet, emptySet(), now)))
    }

    @Test fun no_flood_on_first_run_after_install() {
        val many = (1..50).map { t("x$it", "done", startedAgo = 3 * 3600.0 + it) }
        assertEquals(0, TaskNotifs.decideTaskNotifs(emptyMap(), many, emptySet(), now).size)
    }

    @Test fun titles() {
        assertEquals("Coder finished", TaskNotifs.title("Coder", t("a", "done")))
        assertEquals("Coder stopped", TaskNotifs.title("Coder", t("a", "failed")))
        assertEquals("Fix login · 5 min", TaskNotifs.text(t("a", "done", startedAgo = 600.0, lastAgo = 300.0), now))
    }
}
