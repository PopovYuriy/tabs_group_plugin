package com.github.popovyuriy.tabsgroupplugin.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [GroupStore] has no platform dependencies, so the ordering, pinning and path rules can be
 * covered by plain JUnit. These are exactly the paths that carried the bugs fixed in 1.1.0.
 */
class GroupStoreTest {

    private lateinit var state: TabGroupsState
    private lateinit var store: GroupStore

    @Before
    fun setUp() {
        state = TabGroupsState()
        store = GroupStore(state)
    }

    private fun group(id: String, vararg paths: String) = GroupState().also {
        it.id = id
        it.name = id
        it.colorId = "BLUE"
        it.filePaths = paths.toMutableList()
    }

    private fun ids(groups: List<GroupState>) = groups.map { it.id }

    // ---------- reads must not mutate ----------

    @Test
    fun `reading an unknown branch does not create a bucket`() {
        assertTrue(store.branchGroups("feature/x").isEmpty())
        assertTrue(state.branchGroups.isEmpty())
    }

    @Test
    fun `writing to a branch creates its bucket`() {
        store.addGroup("feature/x", group("a"))
        assertEquals(setOf("feature/x"), state.branchGroups.keys)
    }

    // ---------- the no-branch sentinel ----------

    @Test
    fun `a branch literally named default is separate from the no-branch bucket`() {
        store.addGroup(GroupStore.NO_BRANCH, group("no-vcs"))
        store.addGroup("default", group("real-branch"))

        assertEquals(listOf("no-vcs"), ids(store.branchGroups(GroupStore.NO_BRANCH)))
        assertEquals(listOf("real-branch"), ids(store.branchGroups("default")))
    }

    // ---------- ordering ----------

    @Test
    fun `groups reorder within the branch section`() {
        listOf("a", "b", "c").forEach { store.addGroup("main", group(it)) }

        assertTrue(store.moveGroup("main", "c", -1))
        assertEquals(listOf("a", "c", "b"), ids(store.branchGroups("main")))
    }

    @Test
    fun `the first branch group cannot move up into the pinned section`() {
        store.addGroup("main", group("pinned"))
        store.addGroup("main", group("plain"))
        store.pin("main", "pinned")

        assertFalse(store.canMoveGroup("main", "plain", -1))
        assertFalse(store.moveGroup("main", "plain", -1))
    }

    @Test
    fun `the last pinned group cannot move down into the branch section`() {
        store.addGroup("main", group("pinned"))
        store.addGroup("main", group("plain"))
        store.pin("main", "pinned")

        assertFalse(store.canMoveGroup("main", "pinned", 1))
    }

    @Test
    fun `pinned groups reorder among themselves`() {
        listOf("a", "b").forEach { store.addGroup("main", group(it)) }
        store.pin("main", "a")
        store.pin("main", "b")

        assertTrue(store.moveGroup("main", "b", -1))
        assertEquals(listOf("b", "a"), ids(store.pinnedGroups()))
    }

    // ---------- pin / unpin ----------

    @Test
    fun `pinning appends rather than reusing the branch index`() {
        listOf("a", "b", "c").forEach { store.addGroup("main", group(it)) }
        store.pin("main", "a")
        store.pin("main", "c")

        assertEquals(listOf("a", "c"), ids(store.pinnedGroups()))
        assertEquals(listOf("b"), ids(store.branchGroups("main")))
    }

    @Test
    fun `unpinning returns the group to the current branch`() {
        store.addGroup("main", group("a"))
        store.pin("main", "a")
        store.unpin("feature", "a")

        assertTrue(store.pinnedGroups().isEmpty())
        assertEquals(listOf("a"), ids(store.branchGroups("feature")))
        assertFalse(store.find("feature", "a")!!.isPinned)
    }

    @Test
    fun `pinned groups are visible from every branch`() {
        store.addGroup("main", group("shared"))
        store.pin("main", "shared")
        store.addGroup("feature", group("local"))

        assertEquals(listOf("shared", "local"), ids(store.allGroups("feature")))
    }

    // ---------- paths ----------

    @Test
    fun `renaming a directory re-points every file underneath it`() {
        store.addGroup("main", group("a", "src/old/One.kt", "src/old/nested/Two.kt", "src/other/Three.kt"))

        assertTrue(store.replacePathPrefix("main", "src/old", "src/new"))
        assertEquals(
            listOf("src/new/One.kt", "src/new/nested/Two.kt", "src/other/Three.kt"),
            store.find("main", "a")!!.filePaths
        )
    }

    @Test
    fun `a prefix match must be on a path boundary`() {
        store.addGroup("main", group("a", "src/oldest/One.kt"))

        assertFalse(store.replacePathPrefix("main", "src/old", "src/new"))
        assertEquals(listOf("src/oldest/One.kt"), store.find("main", "a")!!.filePaths)
    }

    @Test
    fun `deleting a directory drops its files`() {
        store.addGroup("main", group("a", "src/gone/One.kt", "src/kept/Two.kt"))

        assertTrue(store.removePathPrefix("main", "src/gone"))
        assertEquals(listOf("src/kept/Two.kt"), store.find("main", "a")!!.filePaths)
    }

    @Test
    fun `files sort by extension then name`() {
        store.addGroup("main", group("a", "b/Zebra.kt", "a/apple.java", "c/beta.kt"))

        assertTrue(store.sortFiles("main", "a"))
        assertEquals(
            listOf("a/apple.java", "c/beta.kt", "b/Zebra.kt"),
            store.find("main", "a")!!.filePaths
        )
    }

    // ---------- maintenance ----------

    @Test
    fun `reconcile repairs the pinned flag and drops empty buckets`() {
        val stale = group("a").also { it.isPinned = true }
        store.addGroup("main", stale)
        state.branchGroups["abandoned"] = mutableListOf()

        store.reconcile()

        assertFalse(store.find("main", "a")!!.isPinned)
        assertFalse(state.branchGroups.containsKey("abandoned"))
    }
}