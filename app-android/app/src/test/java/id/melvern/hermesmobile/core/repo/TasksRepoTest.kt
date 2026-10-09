package id.melvern.hermesmobile.core.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TasksRepoTest {
    private val body = """{"tasks":[
        {"profile":"coder","id":"s1","title":"Fix login","started_at":1000.0,"ended_at":null,"message_count":12,
         "last_activity":1300.5,"status":"running","result":""},
        {"profile":"qa","id":"s2","title":"","started_at":900,"ended_at":1000,"message_count":4,
         "last_activity":990,"status":"done","result":"All green"},
        {"id":"no-profile","started_at":1}
    ]}"""

    @Test fun parse_maps_fields_and_drops_bad_rows() {
        val t = TasksRepo.parse(body)!!
        assertEquals(2, t.size)
        assertEquals("coder", t[0].profile)
        assertTrue(t[0].isRunning)
        assertNull(t[0].endedAt)
        assertEquals(1300.5, t[0].lastActivity, 0.0)
        assertEquals("Task", t[1].title)
        assertEquals("All green", t[1].result)
    }

    @Test fun parse_empty_vs_failure() {
        assertEquals(emptyList<BotTask>(), TasksRepo.parse("""{"tasks":[]}"""))
        assertNull(TasksRepo.parse("""{"error":"x"}"""))
        assertNull(TasksRepo.parse("<html>"))
    }

    @Test fun chat_route_carries_profile() {
        val t = TasksRepo.parse(body)!![0]
        assertEquals("s1|t=Fix%20login|p=coder", t.chatRoute)
    }

    @Test fun duration_labels() {
        assertEquals("45 s", TasksRepo.durationLabel(0.0, 45.0))
        assertEquals("32 min", TasksRepo.durationLabel(0.0, 32 * 60.0 + 10))
        assertEquals("1 h 05 min", TasksRepo.durationLabel(0.0, 3600.0 + 5 * 60))
        assertEquals("0 s", TasksRepo.durationLabel(100.0, 50.0))
        val t = TasksRepo.parse(body)!!
        assertEquals("1 min", TasksRepo.durationLabel(t[1], nowEpoch = 99999.0)) // pakai ended_at
        assertEquals("5 min", TasksRepo.durationLabel(t[0], nowEpoch = 1300.0))  // running → now
    }
}
