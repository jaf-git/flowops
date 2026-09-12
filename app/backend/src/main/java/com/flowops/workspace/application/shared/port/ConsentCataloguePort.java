package com.flowops.workspace.application.shared.port;

public interface ConsentCataloguePort {
    record ConsentText(String language, String version, String text) {}

    ConsentText inLanguage(String language);
}
