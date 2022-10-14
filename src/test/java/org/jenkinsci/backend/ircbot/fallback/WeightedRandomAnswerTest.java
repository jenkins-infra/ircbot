package org.jenkinsci.backend.ircbot.fallback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WeightedRandomAnswerTest {

    @Test
    public void smoke() {
        final String answer = new WeightedRandomAnswer()
                .addAnswer("You're welcome", 5)
                .get();
        assertNotNull(answer);
        assertNotEquals("", answer.trim());
    }

    /**
     * This test runs the get() a lot of times, and checks the distribution looks roughly good.
     * This could theoretically fail, but should generally not (there is a margin of error used below, raise it if deemed finally too low).
     */
    @Test
    public void getAllAnswers() {
        final WeightedRandomAnswer answer = new WeightedRandomAnswer()
                .addAnswer("A", 80)
                .addAnswer("B", 15)
                .addAnswer("C", 5);

        int gotA = 0;
        int gotB = 0;
        int gotC = 0;
        for (int i = 0; i < 100_000; ++i) {
            if (answer.get().equals("A")) {
                gotA++;
            }
            if (answer.get().equals("B")) {
                gotB++;
            }
            if (answer.get().equals("C")) {
                gotC++;
            }
        }
        assertTrue(79_000 < gotA && gotA < 81_000, "Got A " + gotA + " times");
        assertTrue(14_500 < gotB && gotB < 15_500, "Got B " + gotB + " times");
        assertTrue(4_500 < gotC && gotC < 5_500, "Got C " + gotC + " times");
    }
}
