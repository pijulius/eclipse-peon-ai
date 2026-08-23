package org.sterl.llmpeon.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.sterl.llmpeon.StreamMock;
import org.sterl.llmpeon.agent.AiAgent;
import org.sterl.llmpeon.agent.AiDevAgent;
import org.sterl.llmpeon.agent.AiPlanAgent;
import org.sterl.llmpeon.agent.AiPoAgent;
import org.sterl.llmpeon.ai.AiProvider;
import org.sterl.llmpeon.ai.ConfiguredChatModel;
import org.sterl.llmpeon.ai.LlmConfig;
import org.sterl.llmpeon.context.ContextItem;
import org.sterl.llmpeon.context.UserContext;
import org.sterl.llmpeon.parts.PeonAiService;
import org.sterl.llmpeon.parts.shared.JdtUtil;
import org.sterl.llmpeon.parts.tools.EclipseWorkspaceWriteFileTool;
import org.sterl.llmpeon.parts.tools.PlanTool;
import org.sterl.llmpeon.parts.tools.memory.WorkspaceMemoryTool;
import org.sterl.llmpeon.scaffold.AiScaffoldAgent;
import org.sterl.llmpeon.scaffold.ReloadConfigTool;
import org.sterl.llmpeon.shared.ChatMessageUtil;
import org.sterl.llmpeon.tool.tools.CompactSessionTool;
import org.sterl.llmpeon.tool.tools.DiskFileReadTool;
import org.sterl.llmpeon.tool.tools.DiskFileWriteTool;
import org.sterl.llmpeon.tool.tools.DiskGrepTool;
import org.sterl.llmpeon.tool.tools.JonDelegateTool;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;

public class PeonAiServiceTest extends AbstractIntegrationTest {

    private PeonAiService aiService;
    private final StreamMock streamMock = new StreamMock();

    @Before
    public void beforeEach() {
        var ccm = new ConfiguredChatModel(LlmConfig.builder()
                .model("test")
                .url("http://localhost:0")
                .configDir(Path.of(System.getProperty("java.io.tmpdir"), ".peon-test"))
                .build()
        );
        aiService = new PeonAiService(() -> {}, null, null, null, ccm);
        aiService.setProject(project);
        aiService.clearAll();
        ccm.setChatModel(streamMock.buildOkMock());
        aiService.setProject(project);
    }
    
    @Test
    public void test_compact_tool() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        var compressor = aiService.getToolService().getTool(CompactSessionTool.class);
        assertIsPresent(compressor);
        
        // AND
        var comp = aiService.getToolService().toolSpecifications().stream()
            .filter(t -> t.name().equalsIgnoreCase(CompactSessionTool.NAME))
            .findAny();
        assertIsPresent(comp);
    }
    
    @Test
    public void test_onHandoff() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(aiService.getAgents().stream().filter(a -> a.getName().equals(AiPlanAgent.NAME)).findFirst().orElseThrow());
        // AND
        aiService.getActiveAgent().getMemory().clear();
        aiService.setProject(project);
        aiService.getToolService().getTool(EclipseWorkspaceWriteFileTool.class).get().eclipseDeleteResource("peon-plan");
        
        // WHEN
        assertFalse(aiService.onHandoff());
        // AND
        aiService.getActiveAgent().getMemory().add(AiMessage.from("Very good plan"));
        assertTrue(aiService.onHandoff());
        
        // THEN
        assertEquals(AiDevAgent.NAME, aiService.getActiveAgent().getName());
        var messages = aiService.getActiveAgent().getMemory().getCopy();
        assertHasUserMessageWith(messages, "Very good plan");
        // AND no plan ref
        assertHasNoUserMessageWith(messages, PlanTool.OVERVIEW_FILE);
        
    }

    @Test
    public void test_switch_disk_off() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.updateConfig(aiService.getConfig().toBuilder().diskToolsEnabled(true).build());
        assertIsPresent(aiService.getToolService().getTool(DiskGrepTool.class));
        assertIsPresent(aiService.getToolService().getTool(DiskFileReadTool.class));
        assertIsPresent(aiService.getToolService().getTool(DiskFileWriteTool.class));
        
        // WHEN
        aiService.updateConfig(aiService.getConfig().toBuilder().diskToolsEnabled(false).build());
        
        // THEN
        assertIsEmpty(aiService.getToolService().getTool(DiskGrepTool.class));
        assertIsEmpty(aiService.getToolService().getTool(DiskFileReadTool.class));
        assertIsEmpty(aiService.getToolService().getTool(DiskFileWriteTool.class));
        
        assertIsPresent(aiService.getToolService().getTool(CompactSessionTool.class));
    }
    
    @Test
    public void test_message_order() throws IOException {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("Pong"));
        assertTrue(Files.exists(Path.of("../skills")));
        aiService.getSkillService().refresh(Path.of("../skills"));

        // WHEN
        aiService.getActiveAgent().call("Ping", null);

        // THEN — find user message with "Ping" and AI response with "Pong"
        var msg = aiService.getActiveAgent().getMemory().getCopy();
        var userMsg = msg.stream().filter(m -> m instanceof UserMessage).map(UserMessage.class::cast).toList();
        var aiMsg = msg.stream().filter(m -> m instanceof AiMessage).map(AiMessage.class::cast).toList();
        assertTrue("Should have user message", userMsg.size() >= 1);
        assertTrue("Should have AI message", aiMsg.size() >= 1);
        assertEquals("Pong", aiMsg.get(aiMsg.size() - 1).text());
    }
    
    @Test
    public void test_has_read_skill_tool() throws IOException {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("Pong"));
        
        // WHEN
        aiService.getActiveAgent().call("Ping", null);
        
        // THEN
        assertNotNull(mockLlmServer.getCapturedTool("readSkill"));
    }
    
    @Test
    public void test_has_agents_md() throws IOException {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl())
                .build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("Pong"));
        mockLlmServer.queueResponse(AiMessage.aiMessage("compressed")); // for compact
        eclipseWriteFile("AGENTS.md", "# Test Specifics");

        // WHEN
        aiService.call("Ping", null);

        // AND — turn context is restored after compact (not injected on first call)
        aiService.getActiveAgent().compressContext(null);
        var memory = aiService.getActiveAgent().getMemory().getCopy();
        assertHasUserMessageWith(memory, "# Test Specifics");
    }

    @Test
    public void testHandoffStandingOrderWithPlan() {
        // GIVEN: plan agent with a saved plan
        //assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var plan = """
                # Test Plan
                asdasdsad
                asdasdasd
                asdasddas
                """;
        aiService.setActiveAgent(AiPlanAgent.NAME);
        aiService.getToolService().getTool(PlanTool.class).get().planSave(plan);

        // WHEN: handoff occurs
        boolean handedOff = aiService.onHandoff();
        aiService.call(null, null);

        // THEN: handoff succeeded and agent switched
        assertTrue("handoff should succeed with a plan", handedOff);
        assertEquals(AiDevAgent.NAME, aiService.getActiveAgent().getName());

        // AND: plan path + content land in the FIRST user message of the dev agent
        // (context folded into the user message, not a separate/later one — survives truncateMiddle)
        var firstMsg = aiService.getActiveAgent().getMemory().getCopy().get(0);
        assertTrue("first message must be a UserMessage", firstMsg instanceof UserMessage);
        var firstText = ChatMessageUtil.toString(firstMsg);
        assertContains(firstText, "peon-plan/overview.md"); // plan path
        assertContains(firstText, "# Test Plan");           // plan content

        // AND: first get() returns the handoff standing order (rendered)
        streamMock.assertCount("Handover from " + AiPlanAgent.NAME, 1);

        // AND: second get() contains still the reference to the plan
        aiService.call("Rückfrage", null);
        streamMock.assertCount(plan, 1);
        streamMock.assertCount("Handover from", 1);
        streamMock.assertCount("peon-plan/overview.md", 1);
    }
    
    @Test
    public void test_update_token_limit() throws IOException {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var config = aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build();
        aiService.updateConfig(config);

        // WHEN
        aiService.updateConfig(config.toBuilder().autoCompactAfter(4000).build());

        // THEN
        assertEquals(4000, aiService.getConfig().getAutoCompactAfter());
    }
    
    @Test
    public void test_AiScaffoldAgent_tools() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var config = aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build();
        aiService.updateConfig(config);
        aiService.setActiveAgent(AiScaffoldAgent.NAME);

        // WHEN — set turn context supplier and compact to restore context
        var agent = aiService.getActiveAgent();
        mockLlmServer.queueResponse(AiMessage.aiMessage("compressed"));
        agent.getMemory().add(UserMessage.from("pre-compact"));
        agent.compressContext(null);

        // THEN turn context restored after compact contains tool descriptions
        var memory = agent.getMemory().getCopy();
        assertHasUserMessageWith(memory, "- memoryAdd:");
    }
    
    @Test
    public void test_dedup_messages() {
        // GIVEN
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var config = aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build();

        aiService.updateConfig(config);
        aiService.setActiveAgent(AiDevAgent.NAME);
        aiService.getActiveAgent().getMemory().add(UserMessage.from("Text 1"));
        aiService.getActiveAgent().getMemory().add(UserMessage.from("Text 2"));

        // WHEN — set turn context supplier and compact to inject items into memory
        aiService.getActiveAgent().setTurnContextSupplier(() -> List.of(
                new org.sterl.llmpeon.context.SimpleContextItem("Text 1"),
                new org.sterl.llmpeon.context.SimpleContextItem("Text 2"),
                new org.sterl.llmpeon.context.SimpleContextItem("Text 3"),
                new org.sterl.llmpeon.context.SimpleContextItem("Text 3"),
                new org.sterl.llmpeon.context.SimpleContextItem("Unique")));
        mockLlmServer.queueResponse(AiMessage.aiMessage("compressed"));
        aiService.getActiveAgent().compressContext(null);
        aiService.getActiveAgent().call("Text 1", null);
        
        // THEN — verify turn context items reached memory and duplicates were handled
        var memory = aiService.getActiveAgent().getMemory().getCopy();
        var allTexts = memory.stream()
                .map(m -> org.sterl.llmpeon.shared.ChatMessageUtil.toString(m))
                .toList();

        // Text 1 appears at least once (from turn context + call message)
        assertTrue("Text 1 should appear in memory", allTexts.stream().anyMatch(t -> t.contains("Text 1")));
        
        // Text 2 appears once from turn context
        assertEquals("Text 2 should appear once", 1, allTexts.stream().filter(t -> t.contains("Text 2")).count());
        
        // Text 3 was dedupped by restoreTurnContext: only one occurrence despite supplier providing it twice
        assertEquals("Text 3 should appear once (dedupped)", 1, allTexts.stream().filter(t -> t.contains("Text 3")).count());
        
        // Unique appears once
        assertEquals("Unique should appear once", 1, allTexts.stream().filter(t -> t.contains("Unique")).count());
    }
    
    /** Jon must be the first entry in the agent dropdown. */
    @Test
    public void test_po_is_first_agent() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        assertEquals(AiPoAgent.NAME, aiService.getAgents().get(0).getName());
    }

    /**
     * Regression: opening Jon first used to yield "No model configured" because AiPoAgent inherited the
     * no-op setAgentModelName default. Jon now uses the plan model slot and defaults to the dev/main model.
     */
    @Test
    public void test_po_model_uses_plan_slot_and_defaults_to_dev_model() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        // GIVEN a dev/default model, no plan model, Jon active
        aiService.updateConfig(aiService.getConfig().toBuilder().model("dev-model").planModel(null).build());
        aiService.setActiveAgent(AiPoAgent.NAME);

        // THEN Jon defaults to the dev/main model (never empty -> no "No model configured")
        assertEquals("dev-model", aiService.getActiveModel());

        // WHEN a model is selected while Jon is active (used to be dropped for PO)
        assertTrue(aiService.getActiveAgent().setAgentModelName("po-model"));

        // THEN it lands in the plan slot and is what Jon reports and runs on
        assertEquals("po-model", aiService.getActiveModel());
        assertEquals("po-model", aiService.getConfig().getPlanModel());
    }

    /**
     * "Die Info": on an empty workspace with no docs/index.md, Jon greets the user with a one-time chat
     * tutorial. It is the clean complement of the docs index (which rides in his turn context,
     * ADR-0029) — never shown once an index exists and never once Jon has state.
     * Regression for the getPoTutorial trigger.
     */
    @Test
    public void test_po_tutorial_only_without_docs_index() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        eclipseDeleteResource("docs/index.md");
        aiService.setProject(project);
        aiService.setActiveAgent(AiPoAgent.NAME);
        aiService.getActiveAgent().getMemory().clear();

        // no index + empty memory -> the intro is offered
        var intro = aiService.getPoTutorial();
        assertNotNull(intro);
        assertContains(intro, "Jon");

        // once Jon has state, no more intro (empty-memory guard)
        aiService.getActiveAgent().getMemory().add(UserMessage.from("hi"));
        assertNull(aiService.getPoTutorial());

        // and when an index exists, Jon navigates it -> no intro even with empty memory
        aiService.getActiveAgent().getMemory().clear();
        eclipseWriteFile("docs/index.md", "# Docs Index");
        assertNull(aiService.getPoTutorial());
    }

    /** Jon carries his delegate tools (talk/plan/ask/build) plus his own search sub-agent. */
    @Test
    public void test_po_has_delegate_tools() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);

        var names = aiService.getActiveAgent().getToolService().toolSpecifications().stream()
                .map(t -> t.name()).toList();
        assertTrue("talkPlan expected", names.contains("talkPlan"));
        assertTrue("planWithPlanAgent expected", names.contains("planWithPlanAgent"));
        assertTrue("askDev expected", names.contains("askDev"));
        assertTrue("buildWithDev expected", names.contains("buildWithDev"));
        assertTrue("searchAgent expected", names.contains("searchAgent"));
    }

    /**
     * Jon owns the shared memory (write) — he steers all agents through it — plus read-only plan access
     * (hasPlan/planRead). He never writes plans himself (that is delegated to his Peon-Plan slave).
     */
    @Test
    public void test_po_has_memory_write_and_readonly_plan() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);

        var names = aiService.getActiveAgent().getToolService().toolSpecifications().stream()
                .map(t -> t.name()).toList();
        assertTrue("memoryAdd (write) expected", names.contains("memoryAdd"));
        assertTrue("planRead expected", names.contains("planRead"));
        assertTrue("hasPlan expected", names.contains("hasPlan"));
        // Jon delegates planning — he must not be able to write the plan himself
        assertFalse("planSave must not be Jon's tool", names.contains("planSave"));
    }

    /** Slaves may READ the shared memory (injected) but must NOT be able to write it. */
    @Test
    public void test_po_slaves_cannot_write_memory() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);
        var jon = aiService.getActiveAgent();

        var delegate = jon.getToolService().getTool(JonDelegateTool.class).orElseThrow();
        var memoryWrite = delegate.getPlanSlave().getToolService().getExecutor("memoryAdd");
        assertNotNull("memoryAdd executor is present in the shared tool set", memoryWrite);

        assertFalse("Plan slave cannot write memory", delegate.getPlanSlave().isToolActive(memoryWrite));
        assertFalse("Dev slave cannot write memory", delegate.getDevSlave().isToolActive(memoryWrite));
    }

    /**
     * ADR-0024: Jon keeps a durable (persisted) memory, but his Plan/Dev slaves are RAM-only and are
     * distinct instances from the user-selectable Peon-Plan / Peon-Dev agents.
     */
    @Test
    public void test_po_slaves_are_ram_only_jon_is_durable() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);
        var jon = aiService.getActiveAgent();

        assertTrue("Jon persists his state", jon.getMemory().isPersistent());

        var delegate = jon.getToolService().getTool(JonDelegateTool.class).orElseThrow();
        assertFalse("Plan slave is RAM-only", delegate.getPlanSlave().getMemory().isPersistent());
        assertFalse("Dev slave is RAM-only", delegate.getDevSlave().getMemory().isPersistent());

        // distinct from the user-selectable standalone agents
        assertNotSame(aiService.getAgent(AiPlanAgent.NAME).get(), delegate.getPlanSlave());
        assertNotSame(aiService.getAgent(AiDevAgent.NAME).get(), delegate.getDevSlave());
    }

    /**
     * Inc 2 (docs/sklaven-kontext-plan.md): the static context (date/OS + file-access rules) must reach
     * Jon's RAM slaves too — they are not in agentService, so setStaticContext applies it directly.
     */
    @Test
    public void test_static_context_reaches_jons_slaves() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);
        var delegate = aiService.getActiveAgent().getToolService().getTool(JonDelegateTool.class).orElseThrow();

        assertTrue("Plan slave got the static context", !delegate.getPlanSlave().getStaticContext().isEmpty());
        assertTrue("Dev slave got the static context", !delegate.getDevSlave().getStaticContext().isEmpty());
    }

    // --- Header status widget MVP (agenten-status-im-header-mvp-plan.md, ADR-0025) -------------

    /** GIVEN Jon is active THEN getStatusAgents() is his team: Da Boss first, then Da Thinka, Da Mek. */
    @Test
    public void test_status_agents_are_jons_team_when_po_active() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);

        var uiNames = aiService.getStatusAgents().stream().map(n -> n.uiName()).toList();
        assertEquals(List.of("Da Boss", "Da Thinka", "Da Mek"), uiNames);
    }

    /** GIVEN any other agent is active THEN the status widget shows nothing (empty list). */
    @Test
    public void test_status_agents_empty_for_other_agents() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiDevAgent.NAME);
        assertTrue("only Jon shows a status team", aiService.getStatusAgents().isEmpty());
    }

    /**
     * The status list follows the active agent with no stale state: each {@code getStatusAgents()}
     * re-reads the live IST, so switching PO → Dev flips team → empty on the very next call (the old
     * roster's cache-freeness regression, now on the pull choke-point).
     */
    @Test
    public void test_status_agents_follow_switch_no_stale_state() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);
        assertFalse("Jon shows his team", aiService.getStatusAgents().isEmpty());

        aiService.setActiveAgent(AiDevAgent.NAME);
        assertTrue("switching away clears it immediately", aiService.getStatusAgents().isEmpty());
    }

    // --- Inc 3: ContextItem Integration Tests ---------------------------------

    /**
     * The static (persistent) context is exactly Env + Workspace-Memory — no file items
     * (docs/memory.md, docs/index.md, AGENTS.md ride in the turn context, ADR-0029).
     * Deterministic: the memory entry is persisted BEFORE the service build (the constructor's
     * updateConfig/initStaticContext bakes the persisted memory into the static context) and is
     * reset in a finally so it never leaks into other tests/runs.
     */
    @Test
    public void test_staticContext_isEnvPlusMemory() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN: a memory entry persisted BEFORE the service build (baked into the static context)
        var wmt = new WorkspaceMemoryTool();
        wmt.memoryReset();
        String memoryText = "static context memory entry";
        wmt.memoryAdd(memoryText);
        try {
            // AND: file items that must NOT land in the static context
            eclipseWriteFile("docs/memory.md", "# Memory\n- project context");
            eclipseWriteFile("docs/index.md", "# Docs Index\n- feature-x");

            // Frischer Service: updateConfig/initStaticContext backt das persistierte Memory ein.
            var ccm = new ConfiguredChatModel(LlmConfig.builder()
                    .model("test").url("http://localhost:0")
                    .configDir(Path.of(System.getProperty("java.io.tmpdir"), ".peon-test")).build());
            var svc = new PeonAiService(() -> {}, null, null, null, ccm);
            svc.setProject(project);
            svc.clearAll();
            ccm.setChatModel(streamMock.buildOkMock());
            svc.setProject(project);
            svc.setActiveAgent(AiPoAgent.NAME);

            // THEN: Jon's static context is exactly Env + Memory (2 items)
            var jon = svc.getActiveAgent();
            var ctx = jon.getStaticContext();
            assertEquals(2, ctx.size());
            assertContains(ctx.get(0).render(), "prefer eclipse*"); // Env
            assertContains(ctx.get(1).render(), memoryText);        // Memory

            // AND: no file items (docs/memory.md, docs/index.md) in the static context
            var rendered = ctx.stream().map(ContextItem::render).filter(r -> r != null).toList();
            assertHasNoMessageWith(rendered, "docs/memory.md");
            assertHasNoMessageWith(rendered, "docs/index.md");

            // AND: the slaves share Jon's static context (same list object, Env + Memory)
            var delegate = jon.getToolService().getTool(JonDelegateTool.class).orElseThrow();
            assertSame(jon.getStaticContext(), delegate.getPlanSlave().getStaticContext());
            assertSame(jon.getStaticContext(), delegate.getDevSlave().getStaticContext());
        } finally {
            wmt.memoryReset(); // isoliert: Memory nicht in andere Tests/Runs leaken
        }
    }
    /**
     * F2-Delta (issue-03 main scenario): ReloadConfigTool.reloadConfig() calls agentService.reloadAgents()
     * directly — its callback chain never reaches updateConfig. The PeonAiService constructor wraps the
     * onAgentReload callback so that after the reload, Env+Memory is re-baked into the static context of
     * every agent (new custom agents included) and only then the original UI callback fires.
     */
    @Test
    public void test_reloadConfig_rebakesStaticContext_forNewCustomAgents() throws Exception {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN: a memory entry persisted BEFORE the service build
        var wmt = new WorkspaceMemoryTool();
        wmt.memoryReset();
        String memoryText = "reload rebake memory entry";
        wmt.memoryAdd(memoryText);
        try {
            // AND: a unique config dir with an agent folder — AGENT.md lands only AFTER the build,
            // so the custom agent appears exclusively through the reload
            var configDir = Files.createTempDirectory("peon-reload-test");
            var agentDir = Files.createDirectories(configDir.resolve(LlmConfig.AGENT_DIRECTORY).resolve("TestAgent"));

            var ccm = new ConfiguredChatModel(LlmConfig.builder()
                    .model("test").url("http://localhost:0")
                    .configDir(configDir).build());
            var svcRef = new AtomicReference<PeonAiService>();
            var callbackState = new AtomicReference<String>();
            var svc = new PeonAiService(() -> {}, null, null, () -> {
                // capture at the moment the original UI callback fires: is the re-bake already done?
                var custom = svcRef.get().getAgentService().get("TestAgent");
                callbackState.set(custom.isPresent() && custom.get().getStaticContext().stream()
                        .map(ContextItem::render)
                        .anyMatch(r -> r != null && r.contains(memoryText)) ? "baked" : "not-baked");
            }, ccm);
            svcRef.set(svc);
            svc.setProject(project);
            ccm.setChatModel(streamMock.buildOkMock());
            assertFalse("custom agent must not exist before the reload",
                    svc.getAgentService().get("TestAgent").isPresent());

            // AND: the custom agent exists from here on
            Files.writeString(agentDir.resolve("AGENT.md"),
                    "---\nread-only: true\ninclude-default: true\n---\nYou are TestAgent.\n");

            // WHEN: the scaffold agent reloads the config (direct reloadAgents() path — NOT updateConfig)
            var scaffold = svc.getAgentService().get(AiScaffoldAgent.NAME).orElseThrow();
            var reloadTool = scaffold.getToolService().getTool(ReloadConfigTool.class).orElseThrow();
            reloadTool.reloadConfig();

            // THEN: the new custom agent carries Env + Memory in its static context (system prompt)
            var custom = svc.getAgentService().get("TestAgent").orElseThrow();
            var ctx = custom.getStaticContext();
            assertContains(ctx.get(0).render(), "prefer eclipse*"); // Env
            assertTrue("custom agent static context carries the memory",
                    ctx.stream().map(ContextItem::render).anyMatch(r -> r != null && r.contains(memoryText)));

            // AND: the original callback fired AFTER the re-bake
            assertEquals("baked", callbackState.get());
        } finally {
            wmt.memoryReset(); // isoliert: Memory nicht in andere Tests/Runs leaken
        }
    }

    /** ADR-0029: the turn context carries the project info AND AGENTS.md (no longer in the system prompt). */
    @Test
    public void test_turnContextSupplier_providesProjectInfoAndAgentsMd() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);
        aiService.getActiveAgent().getMemory().clear();

        // GIVEN: AGENTS.md exists (write before setProject so agentsMdService.load picks it up)
        var content = "# Test Specifics";
        eclipseWriteFile("AGENTS.md", content);

        // AND mock server configured
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("compressed summary"));

        // WHEN: compact triggers restoreTurnContext
        aiService.getActiveAgent().compressContext(null);

        // THEN: turn context restored — project info AND AGENTS.md
        var memory = aiService.getActiveAgent().getMemory().getCopy();
        assertHasUserMessageWith(memory, UserContext.PROJECT_TAG);
        assertHasUserMessageWith(memory, content);
    }

    /** S2 (ADR-0029): Jon's docs/memory.md + docs/index.md ride in his turn context (history). */
    @Test
    public void test_turnContext_providesJonFiles() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);
        aiService.getActiveAgent().getMemory().clear();

        // GIVEN: Jon's docs exist
        eclipseWriteFile("docs/memory.md", "# Memory\n- project context");
        eclipseWriteFile("docs/index.md", "# Docs Index\n- feature-x");
        aiService.setProject(project);

        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("compressed"));

        // WHEN: compact triggers restoreTurnContext
        aiService.getActiveAgent().compressContext(null);

        // THEN: both files are injected as history messages (header + content)
        var memory = aiService.getActiveAgent().getMemory().getCopy();
        assertHasUserMessageWith(memory, "docs/memory.md");
        assertHasUserMessageWith(memory, "# Memory");
        assertHasUserMessageWith(memory, "docs/index.md");
        assertHasUserMessageWith(memory, "# Docs Index");

        // AND missing files -> nothing injected, no error
        eclipseDeleteResource("docs/memory.md");
        eclipseDeleteResource("docs/index.md");
        aiService.getActiveAgent().getMemory().clear();
        aiService.getActiveAgent().compressContext(null);
        memory = aiService.getActiveAgent().getMemory().getCopy();
        assertHasNoUserMessageWith(memory, "# Memory");
        assertHasNoUserMessageWith(memory, "# Docs Index");
    }

    /** S5 (ADR-0029): the slaves get AGENTS.md in their turn context via additionalContext. */
    @Test
    public void test_slaves_getAgentsMdInTurnContext() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        eclipseWriteFile("AGENTS.md", "# Slave Test Specifics");
        aiService.setProject(project);
        aiService.setActiveAgent(AiPoAgent.NAME);

        // AND mock server configured for the slave's call
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("plan done"));

        var delegate = aiService.getActiveAgent().getToolService().getTool(JonDelegateTool.class).orElseThrow();

        // WHEN: Jon delegates to his plan slave
        delegate.talkPlan("make a plan");

        // THEN: the plan slave's memory contains the AGENTS.md content (via the additionalContext function)
        assertHasUserMessageWith(delegate.getPlanSlave().getMemory().getCopy(), "# Slave Test Specifics");
    }


    /**
     * SOLL (truncateMiddle survival): a Jon-Tool dispatch that references a plan must land the plan
     * (content + path) in the slave's FIRST user message — together with the prompt, not as a
     * separate/later message — so the plan survives a truncateMiddle compaction in the kept prefix.
     */
    @Test
    public void test_jonToolWithPlan_planInFirstUserMessage() {
        // GIVEN: Jon active, a released plan exists, dev slave (Da Mek) is fresh
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiPoAgent.NAME);
        var delegate = aiService.getActiveAgent().getToolService().getTool(JonDelegateTool.class).orElseThrow();
        String planContent = "# Build Plan\n- step 1\n- step 2";
        aiService.getSharedToolService().getTool(PlanTool.class).get().planSave(planContent);

        // AND: LLM mock for the slave's call
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("build done"));

        // WHEN: Jon dispatches to his dev slave with the plan referenced
        delegate.buildWithDev("implement the plan", PlanTool.OVERVIEW_FILE);

        // THEN: the dev slave's FIRST user message holds prompt AND plan (content + path)
        var firstMsg = delegate.getDevSlave().getMemory().getCopy().get(0);
        assertTrue("first message must be a UserMessage", firstMsg instanceof UserMessage);
        var firstText = ChatMessageUtil.toString(firstMsg);
        assertContains(firstText, "implement the plan");    // the prompt
        assertContains(firstText, "peon-plan/overview.md"); // the plan path
        assertContains(firstText, "# Build Plan");          // the plan content
    }

    // --- ADR-0028: Compact-Delegation Integration Tests -------------------------

    /** ADR-0029: compact restores the turn context for Jon (project + AGENTS.md + his docs survive). */
    @Test
    public void test_compactDelegatesToPoAgent() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN: Jon aktiv, docs/memory.md + docs/index.md + AGENTS.md existieren
        eclipseWriteFile("docs/memory.md", "# Memory\n- project context");
        eclipseWriteFile("docs/index.md", "# Docs Index\n- feature-x");
        eclipseWriteFile("AGENTS.md", "# Test Specifics\nRule 1");
        aiService.setProject(project);
        aiService.setActiveAgent(AiPoAgent.NAME);
        aiService.getActiveAgent().getMemory().clear();

        // Memory füllen (simuliert Konversation vor Compact)
        aiService.getActiveAgent().getMemory().add(UserMessage.from("Talk 1"));
        aiService.getActiveAgent().getMemory().add(AiMessage.from("Reply 1"));

        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("WHAT: Compressed summary of conversation"));

        // WHEN: Compact via Agent
        aiService.getActiveAgent().compressContext(null);

        // THEN: Turn-Context (Project + AGENTS.md + Jons Docs) überlebt als UserMessage in Memory
        var memory = aiService.getActiveAgent().getMemory().getCopy();
        assertHasUserMessageWith(memory, "# Test Specifics");
        assertHasUserMessageWith(memory, "# Memory");
        assertHasUserMessageWith(memory, "# Docs Index");
        assertHasUserMessageWith(memory, "User selected project:");

        // AND: Summary ist in Memory (als AiMessage)
        assertTrue("AI summary should be in memory", memory.stream()
                .anyMatch(m -> m instanceof AiMessage ai && ai.text().contains("WHAT: Compressed summary")));

        // AND: alte Konversation ist weg (check all message texts)
        var allTexts = memory.stream()
                .map(m -> org.sterl.llmpeon.shared.ChatMessageUtil.toString(m))
                .toList();
        assertHasNoMessageWith(allTexts, "Talk 1");
    }

    /** Mixed restore: setUserContextInformations survives compact (restoreUserContext). */
    @Test
    public void test_compactMixedRestore_survives() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN: Dev-Agent mit setUserContextInformations
        aiService.setActiveAgent(AiDevAgent.NAME);
        aiService.getActiveAgent().getMemory().clear();

        aiService.getActiveAgent().getMemory().add(UserMessage.from("old talk"));
        aiService.getActiveAgent().getMemory().add(AiMessage.from("old reply"));

        aiService.getActiveAgent().setTurnContextSupplier(() -> List.of(
                new org.sterl.llmpeon.context.SimpleContextItem("order1: be concise"),
                new org.sterl.llmpeon.context.SimpleContextItem("order2: no filler")));

        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("compressed"));

        // WHEN: Compact via Agent
        aiService.getActiveAgent().compressContext(null);

        // THEN: orders sind im Memory (gerestored via restoreUserContext)
        var memory = aiService.getActiveAgent().getMemory().getCopy();
        assertHasUserMessageWith(memory, "order1: be concise");
        assertHasUserMessageWith(memory, "order2: no filler");

        // AND: Summary (als AiMessage)
        assertTrue("AI summary should be in memory", memory.stream()
                .anyMatch(m -> m instanceof AiMessage ai && ai.text().contains("compressed")));

        // AND: alte Konversation ist weg
        var allTexts = memory.stream()
                .map(m -> org.sterl.llmpeon.shared.ChatMessageUtil.toString(m))
                .toList();
        assertHasNoMessageWith(allTexts, "old talk");
    }

    /** Compact does not duplicate existing context (contains-Check). */
    @Test
    public void test_compactNoDuplicates() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN: Kontext bereits in Memory
        aiService.setActiveAgent(AiDevAgent.NAME);
        aiService.getActiveAgent().getMemory().clear();

        String existingContext = "already in memory";
        aiService.getActiveAgent().getMemory().add(UserMessage.from(existingContext));
        aiService.getActiveAgent().getMemory().add(AiMessage.from("reply"));

        aiService.getActiveAgent().setTurnContextSupplier(() -> List.of(new org.sterl.llmpeon.context.SimpleContextItem(existingContext)));

        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("compressed"));

        // WHEN: Compact
        aiService.getActiveAgent().compressContext(null);

        // THEN: Kontext erscheint NUR EINMAL (contains-Check)
        var memory = aiService.getActiveAgent().getMemory().getCopy();
        var allTexts = memory.stream()
                .map(m -> org.sterl.llmpeon.shared.ChatMessageUtil.toString(m))
                .toList();

        // Count occurrences across all messages
        long count = allTexts.stream()
                .filter(t -> t.contains(existingContext))
                .count();
        assertEquals("Context should appear exactly once (no duplicate after compact)", 1, count);
    }

    /** CompactSessionTool is registered in shared tool service and delegates to agent via compressContext. */
    @Test
    public void test_compactViaTool_delegatesToAgent() {
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());

        // GIVEN: Jon aktiv mit Memory-Inhalt
        eclipseWriteFile("AGENTS.md", "# Delegation Test");
        aiService.setProject(project);
        aiService.setActiveAgent(AiPoAgent.NAME);
        aiService.getActiveAgent().getMemory().clear();
        aiService.getActiveAgent().getMemory().add(UserMessage.from("pre-compact talk"));
        aiService.getActiveAgent().getMemory().add(AiMessage.from("pre-compact reply"));

        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("WHAT: Delegated summary"));

        // CompactSessionTool lives in sharedToolService (Jon's own tool service has no defaults)
        assertIsPresent(aiService.getSharedToolService().getTool(CompactSessionTool.class));

        // WHEN: Compact via agent (the tool delegates to this path via request.getAgent())
        aiService.getActiveAgent().compressContext(null);

        // THEN: Memory enthält Project-Info + AGENTS.md + Summary (ADR-0029)
        var memory = aiService.getActiveAgent().getMemory().getCopy();
        assertHasUserMessageWith(memory, UserContext.PROJECT_TAG);
        assertHasUserMessageWith(memory, "# Delegation Test");
        assertTrue("AI summary should be in memory", memory.stream()
                .anyMatch(m -> m instanceof AiMessage ai && ai.text().contains("WHAT: Delegated summary")));
    }
    // --- Replica tests: issue-01 / issue-02 / KV-cache (append-only) ------------

    /**
     * issue-01 (NPE-Pfad): ohne ausgewähltes Projekt darf der Turn-Context-Pfad
     * ({@code PeonAiService.get()}, {@code _handoffLine == null}-Zweig mit
     * {@code getProject().getFile(PlanTool.OVERVIEW_FILE)}) nicht mit NPE abbrechen —
     * der Turn läuft einfach ohne Plan-Reference durch.
     * Erwartet RED (NPE) = Reproduktion des Issues, kein Fehler im Test.
     */
    @Test
    public void test_turnContext_withoutProject_doesNotNpe() {
        // GIVEN: Dev-Agent aktiv, kein Handoff pending, KEIN Projekt ausgewählt
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiDevAgent.NAME);
        aiService.getActiveAgent().getMemory().clear();
        aiService.getUserContext().setCurrentProject(null); // -> getProject() == null

        // AND: LLM-Mock, damit der Turn nach dem Turn-Context-Pfad komplett laufen kann
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        mockLlmServer.queueResponse(AiMessage.aiMessage("ok"));

        // WHEN: ein Turn wird getriggert (Turn-Context-Pfad, _handoffLine == null Zweig)
        // THEN (SOLL): kein NPE — ohne Projekt läuft der Turn ohne Plan-Reference durch
        aiService.call("hello", null);
    }

    /**
     * issue-02 (Slaven-Memory): das Workspace-Memory gelangt als System-Message in beide
     * Slaven (Da Thinka / Da Mek). Der Slaven-StaticContext wird in {@code initStaticContext()}
     * (via {@code updateConfig()}) aus dem PERSISTIERTEN Memory gebacken und mit dem PO-Agenten
     * geteilt. Deshalb persistiert der Test den Eintrag VOR dem Service-Build und räumt ihn im
     * finally wieder auf — deterministisch, unabhängig vom Workspace-Rest-State.
     */
    @Test
    public void test_slave_systemMessage_contains_workspaceMemory() throws Exception {
        // GIVEN: das Workspace-Memory wird VOR dem Service-Build persistiert.
        // Der Slaven-System-Prompt (StaticContext) wird beim Konstruktor-Call
        // (initStaticContext) aus dem PERSISTIERTEN Memory gebacken und mit dem
        // PO-Agenten geteilt — ein memoryAdd im Test danach ändert ihn nicht.
        // Deshalb muss der Eintrag vor dem Build auf der Platte stehen, sonst
        // hängt das Ergebnis am Workspace-Rest-State (Quelle des Flakes).
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        var wmt = new WorkspaceMemoryTool();
        wmt.memoryReset();
        String memoryText = "Remember: always use tabs, never spaces";
        wmt.memoryAdd(memoryText);
        try {
            // Frischer Service: sein Konstruktor backt das soeben persistierte
            // Memory in den (mit dem PO-Agenten geteilten) Slaven-StaticContext.
            var ccm = new ConfiguredChatModel(LlmConfig.builder()
                    .model("test").url("http://localhost:0")
                    .configDir(Path.of(System.getProperty("java.io.tmpdir"), ".peon-test")).build());
            var svc = new PeonAiService(() -> {}, null, null, null, ccm);
            svc.setProject(project);
            svc.clearAll();
            ccm.setChatModel(streamMock.buildOkMock());
            svc.setProject(project);
            svc.setActiveAgent(AiPoAgent.NAME);
            var delegate = svc.getActiveAgent().getToolService().getTool(JonDelegateTool.class).orElseThrow();

            // AND: LLM-Mock für den Slaven-Call
            svc.updateConfig(svc.getConfig().toBuilder()
                    .providerType(AiProvider.OPEN_AI)
                    .url(mockLlmServer.getUrl()).build());
            mockLlmServer.queueResponse(AiMessage.aiMessage("plan done"));

            // WHEN: Jon delegiert an seinen Plan-Slaven
            delegate.talkPlan("make a plan");

            // THEN (SOLL): das Workspace-Memory ist Teil der SYSTEM-Message des Slaven
            var systemMessage = extractSystemMessage(mockLlmServer.getLastRequestBody());
            assertContains(systemMessage, memoryText);

            // AND: der Dev-Slave (Da Mek) trägt dasselbe Memory in seinem StaticContext (System-Prompt)
            assertTrue("Dev slave static context carries the memory",
                    delegate.getDevSlave().getStaticContext().stream()
                            .map(ContextItem::render)
                            .anyMatch(r -> r != null && r.contains(memoryText)));
        } finally {
            wmt.memoryReset(); // isoliert: Memory nicht in andere Tests/Runs leaken
        }
    }

    /**
     * KV-Cache-Regression ("chat"): die Chat-History wird zwischen Turns nicht angefasst —
     * der bisherige Message-Präfix bleibt byte-stabil (append-only, keine Re-Injektion /
     * Re-Sortierung / Ersetzung vorhandener Messages). Erwartet GREEN.
     */
    @Test
    public void test_chatHistory_appendOnly_betweenTurns() {
        // GIVEN: Dev-Agent, leeres Memory, LLM-Mock, Datei für den Tool-Call
        assumeTrue("Eclipse workspace not available", isWorkspaceAvailable());
        aiService.setActiveAgent(AiDevAgent.NAME);
        aiService.getActiveAgent().getMemory().clear();
        aiService.updateConfig(aiService.getConfig().toBuilder()
                .providerType(AiProvider.OPEN_AI)
                .url(mockLlmServer.getUrl()).build());
        eclipseWriteFile("t3-file.txt", "tool content");

        // WHEN: Turn 1 — LLM antwortet mit Tool-Call, danach finale Antwort
        mockLlmServer.queueResponse(AiMessage.from(ToolExecutionRequest.builder()
                .name("eclipseReadFile")
                .arguments("{\"filePath\": \"" + JdtUtil.pathOf(project) + "/t3-file.txt\"}")
                .build()));
        mockLlmServer.queueResponse(AiMessage.aiMessage("turn1 done"));
        aiService.call("first message", null);
        var prefixAfterTurn1 = snapshotMemory(aiService.getActiveAgent());

        // WHEN: Turn 2 — KEIN clear/compact dazwischen
        mockLlmServer.queueResponse(AiMessage.aiMessage("turn2 done"));
        aiService.call("second message", null);
        var memoryAfterTurn2 = snapshotMemory(aiService.getActiveAgent());

        // THEN: der Turn-1-Präfix ist byte-stabil (append-only)
        assertTrue("history must only grow", memoryAfterTurn2.size() >= prefixAfterTurn1.size());
        for (int i = 0; i < prefixAfterTurn1.size(); i++) {
            assertEquals("message at index " + i + " was modified between turns",
                    prefixAfterTurn1.get(i), memoryAfterTurn2.get(i));
        }
    }

    // --- helpers for the replica tests ------------------------------------------

    /** Serialisiert den Memory-Stand zu stabilen Strings (byte-stabiler Vergleich). */
    private List<String> snapshotMemory(AiAgent agent) {
        return agent.getMemory().getCopy().stream()
                .map(m -> ChatMessageUtil.toString(m))
                .toList();
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Extrahiert die System-Message (role=system) aus dem letzten LLM-Request-Body. */
    private String extractSystemMessage(String body) throws Exception {
        if (body == null) return null;
        var root = JSON.readTree(body);
        for (var msg : root.path("messages")) {
            if ("system".equals(msg.path("role").asText())) {
                var content = msg.path("content");
                return content.isTextual() ? content.asText() : content.toString();
            }
        }
        return null;
    }
}
