package com.javabytes.mcp.prompts;

import java.util.List;

/**
 * The built-in crew pack (issue #18): four prompt templates mapped 1:1 onto
 * the java-anything pipeline phases. The client's agent is the crew;
 * JavaBytes is the foreman's clipboard. Templates use the glossary's
 * migrate vocabulary and reference ADR rules rather than restating them.
 *
 * <p>Tool names appear in backticks; the drift test asserts every
 * backticked tool name exists in the catalog, so a rename or removal can
 * never silently break the crew pack.</p>
 */
public final class CrewPrompts {

    private static final String PIPELINE_CONTEXT = """
            You are migrating a JavaScript/TypeScript source app to a Java target app
            with the JavaBytes MCP server. Vocabulary: this is a migration, not a
            conversion (ADR 0014). You write the port; JavaBytes supplies planning,
            recording, replay and gating. Never claim more than the recorded cases
            prove: behavior not recorded is behavior not proved (ADR 0015).
            """;

    public static List<PromptDefinition> definitions() {
        return List.of(recon(), port(), verify(), report());
    }

    private static PromptDefinition recon() {
        return new PromptDefinition(
                "recon",
                "Analyze the source app and produce the migration ticket list in dependency-ordered waves.",
                List.of(
                        new PromptArgument("source_path",
                                "Path to the JS/TS source app inside an allowed sandbox root", true),
                        new PromptArgument("target",
                                "UI strategy chosen with the human (see the ask-first rules); leave unset only when no human is present and auto applies", false)),
                PIPELINE_CONTEXT + """

                ## Phase: recon

                1. Run `analyze_javascript` on {{source_path}} and read the inventory:
                   frameworks, browser APIs, routes, unsupported constructs.
                2. Run `migrate_plan` on {{source_path}}. It produces units, dependency
                   waves, risks, numbered starter decisions (status needs-owner) and a
                   vocabulary seed. Persist the plan with report_path.
                3. UI strategy (ask-first wizard rules, ADR 0004 as amended by ADR 0013):
                   - When a human is present and no target is pinned, present the
                     strategies with honest one-line recommendations and WAIT for the
                     choice. Recommended for browser webapps: keep-frontend. The
                     default desktop shell is javafx. Vaadin is never a silent fallback.
                   - Only when no human is present may auto apply; the tool states its
                     pick and why. Never silently rewrite a working frontend.
                   - Target for this run: {{target}}
                4. Answer every starter decision with the human and record the answers;
                   a decision stays needs-owner until the owner confirms it.
                5. Turn the plan into tickets: one ticket per unit in wave order, plus
                   one ticket per confirmed decision.

                Stop when: the plan file is written, every decision has an owner
                answer, and the wave-0 ticket list is ready. Do not start porting in
                this phase.
                """);
    }

    private static PromptDefinition port() {
        return new PromptDefinition(
                "port",
                "Port the next wave unit by unit: translate the honest subset, hand-write the rest, record cases for the gate.",
                List.of(
                        new PromptArgument("unit",
                                "Name of the unit from the migration plan to port next", true),
                        new PromptArgument("wave",
                                "Wave number the unit belongs to (from the plan)", false)),
                PIPELINE_CONTEXT + """

                ## Phase: port (unit {{unit}}, wave {{wave}})

                1. Re-read the plan entry for {{unit}}: kind (portable / mixed / ui),
                   risks, dependsOn. If its dependencies have not been ported and
                   gated yet, stop and take the next wave-0 unit instead.
                2. Produce the Java port:
                   - Run `translate_javascript` on the unit for the honest subset.
                   - Hand-write everything the subset does not cover; keep the plan's
                     decisions (identifier strategy, date and time representation,
                     package naming).
                   - Fix-the-generator rule: when generated output is wrong, fix the
                     root cause in the generator or the call parameters and
                     regenerate. Never hand-patch generated files.
                3. Record behavior evidence for the gate: run `migrate_record` on the
                   original unit (Node required; NODE_NOT_FOUND is reported, never
                   silently skipped). Check trap coverage — all five semantic traps
                   should have recorded cases (ADR 0016).
                4. Generate the replay test with `migrate_replay` against the ported
                   class.
                5. List Manual work honestly: every function you hand-wrote or
                   restructured is Manual work; do not fold it into Converted.

                Stop when: the unit compiles, cases are recorded, the replay test
                exists. Verification happens in the verify phase — do not claim the
                unit done here.
                """);
    }

    private static PromptDefinition verify() {
        return new PromptDefinition(
                "verify",
                "Run the migration gate per unit: prove it compiles, require recorded behavior to replay, check the trap catalog.",
                List.of(
                        new PromptArgument("unit",
                                "Name of the ported unit to verify", true),
                        new PromptArgument("project",
                                "Path to the generated Maven project root", true)),
                PIPELINE_CONTEXT + """

                ## Phase: verify (unit {{unit}} in {{project}})

                1. Run `migrate_gate` on {{project}} with the unit's replay test
                   class and cases file. Read the tiered verdict:
                   - Prove: the port compiles.
                   - Gate: 100% of recorded cases replay against the port.
                   - Traps: all five semantic traps have recorded cases.
                2. On GATE_FAILED: read the failing replay cases, fix the root cause
                   in the port (or in the generator, per the fix-the-generator
                   rule), regenerate, and rerun the gate. Never weaken the
                   comparator and never hand-patch generated output to make a case
                   pass.
                3. On GATE_PASSED_WITHOUT_TRAP_EVIDENCE: treat as not verified for
                   the missing dimension — run `migrate_record` with a wider trap
                   selection and replay again.
                4. Unportable recorded values (functions, symbols, deep structures)
                   fail the replay loudly by design: restructure the ported
                   function instead of suppressing the failure.
                5. Save the gate output as target/migrate-gate-{{unit}}.json so the
                   final report carries the verdict.

                Stop when: verdict is GATE_PASS, or you can state exactly which
                dimension failed and why it cannot be fixed without a human.
                """);
    }

    private static PromptDefinition report() {
        return new PromptDefinition(
                "report",
                "Assemble the final migration report with the honest Converted / Manual work / Unverified split.",
                List.of(
                        new PromptArgument("project",
                                "Path to the generated project to report on", true)),
                PIPELINE_CONTEXT + """

                ## Phase: report ({{project}})

                1. Run `migrate_report` on {{project}}. It refuses to report without
                   recorded evidence; that refusal is correct — do not bypass it.
                2. Assemble the three-list split from the report and the gate
                   results:
                   - Converted: units with GATE_PASS — claimed only as far as the
                     recorded cases prove.
                   - Manual work: every function hand-written or restructured,
                     with the reason.
                   - Unverified: units without recorded cases or without a replay
                     test, and every decision still needs-owner.
                3. Deliver the report to the human. Claims end where the evidence
                   ends (ADR 0015): a compile is a claim, a replayed case is
                   evidence.

                Stop when: the report is written and the three lists are delivered.
                """);
    }

    private CrewPrompts() {
    }
}
