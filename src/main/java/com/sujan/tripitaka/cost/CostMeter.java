package com.sujan.tripitaka.cost;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Estimates what each model call costs, from its token usage and the prices in {@code rag.pricing}, and adds it to the
 * {@code tripitaka.ai.cost.usd} metric (see /actuator/metrics/tripitaka.ai.cost.usd, tagged by step and model).
 * An estimate: provider prices change and discounts for cached prompt tokens are not counted.
 */
@Component
public class CostMeter {

    private static final Logger log = LoggerFactory.getLogger(CostMeter.class);

    /** US dollars per million tokens. */
    public record Price(double input, double output) {
    }

    private final MeterRegistry registry;
    private final Map<String, Price> prices;

    public CostMeter(MeterRegistry registry,
                     @Value("${rag.pricing.gpt-5-4.input:2.50}") double strongInput,
                     @Value("${rag.pricing.gpt-5-4.output:15.00}") double strongOutput,
                     @Value("${rag.pricing.gpt-5-4-mini.input:0.75}") double miniInput,
                     @Value("${rag.pricing.gpt-5-4-mini.output:4.50}") double miniOutput,
                     @Value("${rag.pricing.gpt-5-4-nano.input:0.20}") double nanoInput,
                     @Value("${rag.pricing.gpt-5-4-nano.output:1.25}") double nanoOutput) {
        this.registry = registry;
        this.prices = Map.of(
                "gpt-5.4-mini", new Price(miniInput, miniOutput),
                "gpt-5.4-nano", new Price(nanoInput, nanoOutput),
                "gpt-5.4", new Price(strongInput, strongOutput));
    }

    /** The price for a model name as the API reports it, e.g. "gpt-5.4-mini-2026-03-17"; null if unknown. */
    Price priceOf(String model) {
        if (model == null) {
            return null;
        }
        // Longest matching prefix first, so "gpt-5.4-mini-…" is not priced as "gpt-5.4".
        return prices.entrySet().stream()
                .filter(e -> model.startsWith(e.getKey()))
                .max((a, b) -> Integer.compare(a.getKey().length(), b.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    /** Records one model call and returns its estimated cost in US dollars (0 when usage or price is unknown). */
    public double record(String step, String model, Usage usage) {
        if (usage == null) {
            return 0;
        }
        Price price = priceOf(model);
        if (price == null) {
            log.warn("No price configured for model={}; its cost is not counted", model);
            return 0;
        }
        double cost = cost(price, tokens(usage.getPromptTokens()), tokens(usage.getCompletionTokens()));
        Counter.builder("tripitaka.ai.cost.usd")
                .description("Estimated model cost in US dollars")
                .tag("step", step)
                .tag("model", model)
                .register(registry)
                .increment(cost);
        return cost;
    }

    static double cost(Price price, long promptTokens, long completionTokens) {
        return (promptTokens * price.input() + completionTokens * price.output()) / 1_000_000.0;
    }

    private static long tokens(Integer value) {
        return value == null ? 0 : value;
    }
}
