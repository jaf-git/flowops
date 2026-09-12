package com.flowops.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowops.task.domain.exception.LinkSchemeNotAllowedException;
import com.flowops.task.domain.exception.LinkUrlRequiredException;
import com.flowops.task.domain.model.LinkUrl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("TASK-LINK-01")
class LinkUrlTest {
    @ParameterizedTest
    @ValueSource(strings = {"https://drive.example.com/q3-review.xlsx", "http://intranet/brief"})
    void theTwoSchemesAPersonCanUsefullyClickAreKept(String address) {
        assertThat(LinkUrl.of(address).value()).isEqualTo(address);
    }

    @Test
    void aJavascriptAddressIsRefused() {
        assertThatThrownBy(() -> LinkUrl.of("javascript:alert(document.cookie)"))
                .isInstanceOf(LinkSchemeNotAllowedException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
                "file:///C:/Users/andrei/brief.docx",
                "vbscript:msgbox(1)",
                "ftp://files.example.com/brief.docx",
                "mailto:andrei@atelier.ro"
            })
    void everythingOtherThanHttpAndHttpsIsRefused(String address) {
        assertThatThrownBy(() -> LinkUrl.of(address)).isInstanceOf(LinkSchemeNotAllowedException.class);
    }

    @Test
    void anAddressWithNoSchemeIsRefusedRatherThanGuessedAt() {
        assertThatThrownBy(() -> LinkUrl.of("www.example.com")).isInstanceOf(LinkSchemeNotAllowedException.class);
    }

    @Test
    void theSchemeIsJudgedWithoutRegardToCase() {
        assertThat(LinkUrl.of("HTTPS://example.com/brief").value()).isEqualTo("HTTPS://example.com/brief");
        assertThatThrownBy(() -> LinkUrl.of("JavaScript:alert(1)")).isInstanceOf(LinkSchemeNotAllowedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void anAddressOfNothingIsRefused(String blank) {
        assertThatThrownBy(() -> LinkUrl.of(blank)).isInstanceOf(LinkUrlRequiredException.class);
    }

    @Test
    void anAbsentAddressIsRefused() {
        assertThatThrownBy(() -> LinkUrl.of(null)).isInstanceOf(LinkUrlRequiredException.class);
    }

    @Test
    void theHostIsWhatAnUnlabelledLinkShows() {
        assertThat(LinkUrl.of("https://drive.example.com/q3/review.xlsx").host())
                .isEqualTo("drive.example.com");
    }
}
