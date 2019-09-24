package org.jenkinsci.backend.ircbot.fallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Provides a random answer based on the relative weight of each answer this was constructed with.
 */
class WeightedRandomAnswer {
    private final Random random = new Random(System.currentTimeMillis());
    Map<String, Integer> answers = new LinkedHashMap<>();

    WeightedRandomAnswer() {
    }

    public WeightedRandomAnswer addAnswer(String answer, int weight) {
        answers.put(answer, weight);
        return this;
    }

    public String get() {
        List<String> possibleAnswers = new ArrayList<>();
        answers.entrySet().stream().forEach(entrySet ->
                                                    possibleAnswers.addAll(Collections.nCopies(entrySet.getValue(), entrySet.getKey()))
        );

        return possibleAnswers.get(random.nextInt(possibleAnswers.size()));
    }


}
