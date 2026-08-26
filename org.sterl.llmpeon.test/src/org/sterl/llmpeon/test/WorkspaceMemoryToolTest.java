package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;

/**
 * Inc 1 (ADR-0032): WorkspaceMemoryTool as ContextItem — render/dedupKey/label unit tests.
 */
public class WorkspaceMemoryToolTest {

    private final WorkspaceMemoryTool wmt = new WorkspaceMemoryTool();

    @Before
    public void beforeEach() {
        wmt.memoryReset();
    }

    @After
    public void afterEach() {
        wmt.memoryReset();
    }

    /** GIVEN one entry WHEN render() THEN header + numbered list including the entry. */
    @Test
    public void test_render_returnsMemorySnapshot() {
        // GIVEN
        String entry = "always answer in haiku";
        wmt.memoryAdd(entry);

        // WHEN
        var rendered = wmt.render();

        // THEN
        assertNotNull(rendered);
        assertContains(rendered, "Your memory of rules and guidelines and informations for your work:");
        assertContains(rendered, "1. [");
        assertContains(rendered, entry);
    }

    /** GIVEN an empty memory WHEN render() THEN null (item is skipped silently, BDD 4). */
    @Test
    public void test_render_emptyMemory_returnsNull() {
        // GIVEN empty memory (reset)
        // WHEN
        var rendered = wmt.render();

        // THEN
        assertNull(rendered);
    }

    /**
     * GIVEN a dedupKey read AND memoryAdd(E2) WHEN dedupKey() is read again
     * THEN it starts with "workspace-memory#" and differs from D1.
     */
    @Test
    public void test_dedupKey_changesOnMemoryChange() {
        // GIVEN
        wmt.memoryAdd("entry one");
        var d1 = wmt.dedupKey();

        // WHEN
        wmt.memoryAdd("entry two");
        var d2 = wmt.dedupKey();

        // THEN
        assertNotNull(d1);
        assertTrue("key must start with workspace-memory#: " + d1, d1.startsWith("workspace-memory#"));
        assertNotEquals(d1, d2);

        // AND: unchanged entries -> same key again (no duplicate on next turn)
        assertEquals(d2, wmt.dedupKey());
    }

    private static void assertContains(String value, String expected) {
        assertNotNull("Expected to find " + expected, value);
        assertTrue("Expected:\n" + value + "\nto contain:\n" + expected, value.contains(expected));
    }
}
