package com.sujan.tripitaka.cost;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.DefaultUsage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CostMeterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CostMeter meter = new CostMeter(registry, 2.50, 15.00, 0.75, 4.50, 0.20, 1.25);

    @Test
    void pricesDatedModelNamesByTheirFamily() {
        assertEquals(0.75, meter.priceOf("gpt-5.4-mini-2026-03-17").input());
        assertEquals(2.50, meter.priceOf("gpt-5.4-2026-03-05").input());
        assertEquals(0.20, meter.priceOf("gpt-5.4-nano").input());
        assertNull(meter.priceOf("some-other-model"));
    }

    @Test
    void estimatesATypicalAnswerAtAboutTwoAndAHalfCents() {
        // 17,730 prompt and 2,577 completion tokens: the average measured for an answer.
        double cost = meter.record("answer", "gpt-5.4-mini-2026-03-17", new DefaultUsage(17_730, 2_577));

        assertEquals(0.0249, cost, 0.0001);
        assertEquals(cost, registry.get("tripitaka.ai.cost.usd").tag("step", "answer").counter().count(), 1e-9);
    }

    @Test
    void unknownModelOrUsageCostsNothing() {
        assertEquals(0, meter.record("plan", "mystery", new DefaultUsage(1000, 1000)));
        assertEquals(0, meter.record("plan", "gpt-5.4", null));
    }
}
