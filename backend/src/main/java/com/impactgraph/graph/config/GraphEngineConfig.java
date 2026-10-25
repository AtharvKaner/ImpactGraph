package com.impactgraph.graph.config;

import com.impactgraph.graph.CycleDetector;
import com.impactgraph.graph.DefaultCycleDetector;
import com.impactgraph.graph.DefaultImpactAnalyzer;
import com.impactgraph.graph.ImpactAnalyzer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing standard bean instances of the graph engine components.
 * <p>
 * Allows teammates (such as Arjun and Amar) to inject {@link ImpactAnalyzer} and {@link CycleDetector}
 * directly into their Spring services via {@code @Autowired}.
 */
@Configuration
public class GraphEngineConfig {

    @Bean
    public ImpactAnalyzer impactAnalyzer() {
        return new DefaultImpactAnalyzer();
    }

    @Bean
    public CycleDetector cycleDetector() {
        return new DefaultCycleDetector();
    }
}
