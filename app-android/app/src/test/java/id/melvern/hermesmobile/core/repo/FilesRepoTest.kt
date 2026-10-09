package id.melvern.hermesmobile.core.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilesRepoTest {
    @Test fun parse_files() {
        val body = """{"files":[
            {"path":"/Users/m/a.png","name":"a.png","kind":"image","size":1234,"at":1791371552.35,
             "profile":"coder","session_id":"s1","session_title":"Logo work"},
            {"path":"/Users/m/b.pdf","name":"b.pdf","kind":"doc","size":5,"at":10,"profile":"default","session_id":"s2","session_title":""},
            {"name":"no-path"}
        ]}"""
        val f = FilesRepo.parse(body)!!
        assertEquals(2, f.size)
        assertEquals(1234L, f[0].size)
        assertEquals("image", f[0].kind)
        assertEquals("s1|t=Logo%20work|p=coder", f[0].chatRoute)
        assertEquals("s2|t=Chat", f[1].chatRoute)
    }

    @Test fun parse_empty_vs_failure() {
        assertEquals(emptyList<AgentFile>(), FilesRepo.parse("""{"files":[]}"""))
        assertNull(FilesRepo.parse("""{"detail":"bad kind"}"""))
    }
}
