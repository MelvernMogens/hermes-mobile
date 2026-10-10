"""mobile_efforts: per-model effort menu built from Hermes' own request builders."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import mobile_efforts as me  # noqa: E402

try:  # the real-builder tests need the hermes-agent venv (the proxy's interpreter)
    import agent.anthropic_adapter  # noqa: F401
    import agent.transports.codex  # noqa: F401
    from providers import get_provider_profile
    HAVE_HERMES = get_provider_profile("zai") is not None
except Exception:
    HAVE_HERMES = False


def _ladder_map(mapping):
    return lambda level: mapping.get(level)


class BuildMenu(unittest.TestCase):
    def test_adaptive_claude_like(self):
        m = me.build_menu("x", _ladder_map({"minimal": "low", "low": "low", "medium": "medium", "high": "high",
                                            "xhigh": "xhigh", "max": "max", "ultra": "max"}))
        by = {l["level"]: l for l in m["levels"]}
        self.assertTrue(m["known"] and m["dial"])
        self.assertEqual([l for l in me.LADDER if by[l]["native"]], ["low", "medium", "high", "xhigh", "max"])
        self.assertEqual(by["ultra"]["runs_as"], "max")
        self.assertEqual(by["minimal"]["runs_as"], "low")

    def test_glm52_like_has_only_high_and_max(self):
        m = me.build_menu("glm-5.2", _ladder_map({lv: "high" for lv in ("minimal", "low", "medium", "high")}
                                                 | {"xhigh": "max", "max": "max", "ultra": "max"}))
        native = [l["level"] for l in m["levels"] if l["native"]]
        self.assertEqual(native, ["high", "max"])

    def test_unknown_route_offers_whole_ladder(self):
        m = me.build_menu("mystery", None)
        self.assertFalse(m["known"])
        self.assertEqual([l["level"] for l in m["levels"] if l["native"]], list(me.LADDER))

    def test_no_effort_field_means_no_dial(self):
        m = me.build_menu("haiku", _ladder_map({}), can_off=True, toggle=False)
        self.assertFalse(m["can_off"])
        self.assertFalse(m["dial"])
        self.assertEqual(m["levels"], [])

    def test_builder_crash_fails_open(self):
        def boom(level):
            raise RuntimeError("x")
        m = me.build_menu("x", boom)
        self.assertFalse(m["known"])
        self.assertIn("ultra", [l["level"] for l in m["levels"]])

    def test_bespoke_wire_value_runs_as_first_level_producing_it(self):
        m = me.build_menu("x", _ladder_map({"minimal": "turbo", "low": "eco", "medium": "turbo"}))
        by = {l["level"]: l for l in m["levels"]}
        self.assertTrue(by["minimal"]["native"])
        self.assertEqual(by["medium"]["runs_as"], "minimal")
        self.assertIsNone(by["high"]["wire"])


class WireReading(unittest.TestCase):
    def test_reads_every_hermes_effort_shape(self):
        self.assertEqual(me.wire_effort({"top": {"reasoning_effort": "high"}}), "high")
        self.assertEqual(me.wire_effort({"extra": {"reasoning": {"effort": "low"}}}), "low")
        self.assertEqual(me.wire_effort({"top": {"verbosity": "max"}}), "max")
        self.assertEqual(me.wire_effort({"extra": {"thinking_config": {"thinkingLevel": "high"}}}), "high")
        self.assertEqual(me.wire_effort({"extra": {"extra_body": {"google": {"thinking_config": {"thinking_level": "low"}}}}}), "low")
        self.assertEqual(me.wire_effort({"output_config": {"effort": "xhigh"}}), "xhigh")
        self.assertEqual(me.wire_effort({"thinking": {"type": "enabled", "budget_tokens": 4000}}), "budget:4000")
        self.assertIsNone(me.wire_effort({"include": []}))

    def test_disable_must_be_explicit(self):
        self.assertTrue(me.wire_disables({"thinking": {"type": "disabled"}}))
        self.assertTrue(me.wire_disables({"reasoning": {"enabled": False}}))
        self.assertTrue(me.wire_disables({"thinking_config": {"thinkingBudget": 0}}))
        self.assertFalse(me.wire_disables({}))
        self.assertFalse(me.wire_disables({"include": []}))


@unittest.skipUnless(HAVE_HERMES, "needs the hermes-agent venv")
class RealBuilders(unittest.TestCase):
    def test_claude_opus_ultra_runs_as_max(self):
        m = me.effort_menu("anthropic", "claude-opus-5-5")
        by = {l["level"]: l for l in m["levels"]}
        self.assertTrue(m["known"] and m["dial"])
        self.assertTrue(by["xhigh"]["native"] and by["max"]["native"])
        self.assertEqual(by["ultra"]["runs_as"], "max")

    def test_glm53_has_no_xhigh(self):
        m = me.effort_menu("zai", "glm-5.3")
        by = {l["level"]: l for l in m["levels"]}
        self.assertFalse(by["xhigh"]["native"])
        self.assertEqual([l for l in ("low", "medium", "high", "max") if by[l]["native"]], ["low", "medium", "high", "max"])
        self.assertEqual(by["ultra"]["runs_as"], "max")

    def test_off_only_where_hermes_sends_a_disable(self):
        self.assertTrue(me.effort_menu("anthropic", "claude-opus-5-5")["can_off"])
        self.assertTrue(me.effort_menu("zai", "glm-5.3")["can_off"])
        # Responses routes omit reasoning when "off" — the model keeps thinking at its default
        self.assertFalse(me.effort_menu("openai-codex", "gpt-5.6-sol")["can_off"])
        self.assertFalse(me.effort_menu("copilot", "gpt-5.4")["can_off"])

    def test_copilot_gpt5_uses_the_responses_route(self):
        # Hermes sends GPT-5 Copilot models over Responses, where max/ultra land on medium
        by = {l["level"]: l for l in me.effort_menu("copilot", "gpt-5.4")["levels"]}
        self.assertTrue(by["high"]["native"])
        self.assertEqual(by["ultra"]["runs_as"], "medium")

    def test_on_off_toggle_only_when_it_changes_the_request(self):
        glm = me.effort_menu("zai", "glm-4.6")
        self.assertFalse(glm["dial"]); self.assertTrue(glm["can_off"])
        haiku = me.effort_menu("anthropic", "claude-haiku-4-5")
        self.assertFalse(haiku["dial"]); self.assertFalse(haiku["can_off"])

    def test_unknown_answers_are_never_cached(self):
        me._CACHE.clear()
        m = me.effort_menu("no-such-provider", "x")
        self.assertFalse(m["known"])
        self.assertEqual(me._CACHE, {})

    def test_legacy_codex_tops_out_at_xhigh(self):
        m = me.effort_menu("openai-codex", "gpt-5.3-codex")
        by = {l["level"]: l for l in m["levels"]}
        self.assertFalse(by["max"]["native"])
        self.assertEqual(by["ultra"]["runs_as"], "xhigh")


if __name__ == "__main__":
    unittest.main()
