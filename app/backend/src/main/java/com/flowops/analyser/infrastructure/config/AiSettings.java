package com.flowops.analyser.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flowops.ai")
public class AiSettings {
    private boolean enabled = false;

    private String model = "llama3.2:3b";

    private int numCtx = 4096;

    private int budgetPerRun = 200;

    private Map<String, Analyser> analyser = new LinkedHashMap<>();

    public enum Mode {
        OFF,

        ON,

        COMPARE
    }

    public static class Analyser {
        private Mode mode = Mode.OFF;

        private String model;

        private Map<String, Boolean> plugs = new LinkedHashMap<>();

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Map<String, Boolean> getPlugs() {
            return plugs;
        }

        public void setPlugs(Map<String, Boolean> plugs) {
            this.plugs = plugs;
        }
    }

    public boolean isConsulted(String analyserId) {
        return enabled && modeFor(analyserId) != Mode.OFF;
    }

    public Mode modeFor(String analyserId) {
        Analyser settings = analyser.get(analyserId);
        return settings == null ? Mode.OFF : settings.getMode();
    }

    public String modelFor(String analyserId) {
        Analyser settings = analyser.get(analyserId);
        return settings == null || settings.getModel() == null ? model : settings.getModel();
    }

    public boolean isPlugOn(String analyserId, String plug) {
        Analyser settings = analyser.get(analyserId);
        return settings != null && Boolean.TRUE.equals(settings.getPlugs().get(plug));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getNumCtx() {
        return numCtx;
    }

    public void setNumCtx(int numCtx) {
        this.numCtx = numCtx;
    }

    public int getBudgetPerRun() {
        return budgetPerRun;
    }

    public void setBudgetPerRun(int budgetPerRun) {
        this.budgetPerRun = budgetPerRun;
    }

    public Map<String, Analyser> getAnalyser() {
        return analyser;
    }

    public void setAnalyser(Map<String, Analyser> analyser) {
        this.analyser = analyser;
    }
}
