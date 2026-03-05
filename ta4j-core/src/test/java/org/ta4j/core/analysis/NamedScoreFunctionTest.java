/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class NamedScoreFunctionTest {

    @Test
    public void applyDelegatesToScore() {
        NamedScoreFunction<String, Integer> scoreFunction = new NamedScoreFunction<>() {
            @Override
            public String name() {
                return "length";
            }

            @Override
            public Integer score(String input) {
                return input == null ? 0 : input.length();
            }
        };

        assertThat(scoreFunction.name()).isEqualTo("length");
        assertThat(scoreFunction.apply("ta4j")).isEqualTo(4);
        assertThat(scoreFunction.score("wave")).isEqualTo(4);
    }
}
