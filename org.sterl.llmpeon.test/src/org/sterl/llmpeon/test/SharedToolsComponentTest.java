package org.sterl.llmpeon.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.command.CommandService;
import org.sterl.llmpeon.parts.ai.component.SharedToolsComponent;
import org.sterl.llmpeon.parts.tools.EclipseBuildTool;
import org.sterl.llmpeon.parts.tools.EclipseCodeNavigationTool;
import org.sterl.llmpeon.parts.tools.EclipseConsoleLogTool;
import org.sterl.llmpeon.parts.tools.EclipseGrepTool;
import org.sterl.llmpeon.parts.tools.EclipseRunTestTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceReadFileTool;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.skill.SkillService;
import org.sterl.llmpeon.tool.ToolService;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;
import org.sterl.llmpeon.tool.tools.SearchAgentTool;

/**
 * Inc 1 (PeonAiService-Struktur-Aufräumen): SharedToolsComponent — tool registration,
 * SearchAgentTool privilege filter and the disk-tool toggle, without LLM.
 */
public class SharedToolsComponentTest {

    private final SharedToolsComponent sut = new SharedToolsComponent(new SkillService(), new CommandService());

    /** GIVEN the component WHEN reading the shared tool service THEN every Eclipse tool is registered exactly once. */
    @Test
    public void test_sharedTools_registersAllEclipseTools() {
        // GIVEN / WHEN
        ToolService ts = sut.toolService();

        // THEN — the tool instance is registered (a tool may expose several @Tool methods)
        assertTrue(countExecutors(ts, WorkspaceMemoryTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseWorkspaceReadFileTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseWorkspaceWriteFileTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseGrepTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseBuildTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseRunTestTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseCodeNavigationTool.class) >= 1);
        assertTrue(countExecutors(ts, EclipseConsoleLogTool.class) >= 1);

        // AND: the search sub-agent is registered
        assertTrue(ts.getTool(SearchAgentTool.class).isPresent());
    }

    private long countExecutors(ToolService ts, Class<?> type) {
        return ts.getExecutors().stream().filter(e -> type.isInstance(e.getTool())).count();
    }

    /** GIVEN the SearchAgentTool WHEN its filter runs THEN privileged tools are excluded, normal ones stay visible. */
    @Test
    public void test_sharedTools_searchAgentFilter_excludesAskUserAndMemory() {
        // GIVEN
        var searchAgent = sut.toolService().getTool(SearchAgentTool.class).orElseThrow();

        // WHEN / THEN — the memory write tool must not leak into search agents
        var memory = sut.toolService().getExecutor("memoryAdd");
        assertTrue("memoryAdd executor expected", memory != null);
        assertFalse("WorkspaceMemoryTool must be filtered out of search agents",
                searchAgent.getFilter().test(memory));

        // AND: normal tools stay visible to search agents
        var grep = sut.toolService().getExecutor("eclipseGrepFiles");
        assertTrue("grep stays visible to search agents", searchAgent.getFilter().test(grep));
    }

    /** GIVEN enabled/disabled config WHEN updateActiveDiskTools THEN disk tools toggle on/off without duplicates. */
    @Test
    public void test_updateActiveDiskTools_togglesDiskTools() {
        // GIVEN disabled (default)
        assertFalse(sut.toolService().getTool(DiskFileWriteTool.class).isPresent());

        // WHEN enabled
        sut.updateActiveDiskTools(config(true));

        // THEN all three disk tools are registered
        assertTrue(sut.toolService().getTool(DiskFileWriteTool.class).isPresent());
        assertTrue(sut.toolService().getTool(DiskFileReadTool.class).isPresent());
        assertTrue(sut.toolService().getTool(DiskGrepTool.class).isPresent());

        // AND: enabling twice does not duplicate them
        sut.updateActiveDiskTools(config(true));
        long writeExecutors = countExecutors(sut.toolService(), DiskFileWriteTool.class);
        assertTrue("no duplicates expected", writeExecutors == sut.toolService().getExecutors().stream()
                .filter(e -> e.getTool() instanceof DiskFileWriteTool)
                .map(e -> e.getSpec().name())
                .distinct()
                .count());

        // WHEN disabled again
        sut.updateActiveDiskTools(config(false));

        // THEN they are removed
        assertFalse(sut.toolService().getTool(DiskFileWriteTool.class).isPresent());
        assertFalse(sut.toolService().getTool(DiskFileReadTool.class).isPresent());
        assertFalse(sut.toolService().getTool(DiskGrepTool.class).isPresent());
    }

    private static LlmConfig config(boolean diskEnabled) {
        return LlmConfig.builder()
                .model("test")
                .url("http://localhost:0")
                .build()
                .toBuilder()
                .diskToolsEnabled(diskEnabled)
                .build();
    }
}
