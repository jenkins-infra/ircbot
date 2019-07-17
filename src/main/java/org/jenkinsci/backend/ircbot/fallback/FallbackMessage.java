package org.jenkinsci.backend.ircbot.fallback;

import org.apache.commons.lang.StringUtils;
import org.pircbotx.User;

/**
 * This class is mostly to separate the core code from the joke-based one. This is VAI: very artificial intelligence.
 * The code below is generally intended to be called after normal commands have been parsed, and nothing was found.
 * <p>
 * So... Main rule below is to be creative with answers :-).
 */
public class FallbackMessage {
    private final String payload;
    private final User sender;

    public FallbackMessage(String payload, User sender) {
        this.payload = payload;
        this.sender = sender;
    }

    public String answer() {
        if (StringUtils.containsIgnoreCase(payload, "thank")) {
            return new WeightedRandomAnswer()
                    .addAnswer("You're welcome", 5)
                    .get();
        }

        if (StringUtils.startsWithIgnoreCase(payload, "hello")) {
            return "Hello " + sender + "!";
        }

        return new WeightedRandomAnswer()
                .addAnswer("I didn't understand the command", 3)
                .addAnswer("Wut?", 1)
                .get();
    }

}
