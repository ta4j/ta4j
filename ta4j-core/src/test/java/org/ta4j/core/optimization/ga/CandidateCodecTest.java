/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.optimization.ga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class CandidateCodecTest {

    private enum SearchMode {
        FAST, SAFE
    }

    record Candidate(int period, SearchMode mode, boolean enabled) {
    }

    @Test
    void decodeBuildsStableCandidateIdAndTypedValues() {
        CandidateCodec<Candidate> codec = new CandidateCodec<>(
                List.of(ParameterDomain.integerRange("period", 5, 15, 5),
                        ParameterDomain.ofValues("mode", List.of(SearchMode.FAST, SearchMode.SAFE)),
                        ParameterDomain.constrainedBoolean("enabled", false, true)),
                values -> new Candidate(values.get("period", Integer.class), values.get("mode", SearchMode.class),
                        values.get("enabled", Boolean.class)));

        CandidateCodec.DecodedCandidate<Candidate> decoded = codec.decode(List.of(1, 0, 1));

        assertThat(decoded.id()).isEqualTo("period=10, mode=FAST, enabled=true");
        assertThat(decoded.context()).isEqualTo(new Candidate(10, SearchMode.FAST, true));
        assertThat(decoded.parameters().asMap()).containsEntry("period", 10)
                .containsEntry("mode", SearchMode.FAST)
                .containsEntry("enabled", true);
    }

    @Test
    void decodeCanUseCustomStableCandidateIds() {
        CandidateCodec<Candidate> codec = new CandidateCodec<>(
                List.of(ParameterDomain.integerRange("period", 5, 15, 5)),
                values -> new Candidate(values.get("period", Integer.class), SearchMode.FAST, true),
                values -> "SMA [" + values.toStableId() + "]");

        CandidateCodec.DecodedCandidate<Candidate> decoded = codec.decode(List.of(2));

        assertThat(decoded.id()).isEqualTo("SMA [period=15]");
    }

    @Test
    void parameterValueTypeChecksAreExplicit() {
        CandidateCodec<Candidate> codec = new CandidateCodec<>(List.of(ParameterDomain.integerRange("period", 5, 5, 1)),
                values -> new Candidate(values.get("period", Integer.class), SearchMode.FAST, true));

        CandidateCodec.ParameterValues parameters = codec.decode(List.of(0)).parameters();

        assertThatThrownBy(() -> parameters.get("period", String.class)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("period");
    }

    @Test
    void invalidRepresentationsAreRejected() {
        CandidateCodec<Candidate> codec = new CandidateCodec<>(
                List.of(ParameterDomain.integerRange("period", 5, 10, 5)),
                values -> new Candidate(values.get("period", Integer.class), SearchMode.FAST, true));

        assertThatThrownBy(() -> codec.decode(List.of())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("representation size");
        assertThatThrownBy(() -> codec.decode(List.of(9))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alleleIndex");
    }
}
