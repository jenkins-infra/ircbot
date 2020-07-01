package org.jenkinsci.backend.ircbot.hosting;

import java.util.Comparator;
import java.util.HashSet;

public class VerificationMessage implements Comparable<VerificationMessage> {
    private String message;
    private Severity severity;
    private HashSet<VerificationMessage> subItems;

    public VerificationMessage(Severity severity, HashSet<VerificationMessage> subItems, String format, Object... args) {
        this.severity = severity;
        this.subItems = subItems;
        message = String.format(format, args);
    }

    public VerificationMessage(Severity severity, String format, Object... args) {
        this(severity, null, format, args);
    }

    @Override
    public int compareTo(VerificationMessage other) {
        Comparator<HashSet<VerificationMessage>> subItemComparator = (left, right) -> {
            if (left.size() != right.size()) {
                return left.size() - right.size();
            }

            if(!left.containsAll(right)) {
                return -1;
            } else if(!right.containsAll(left)) {
                return 1;
            }
            return 0;
        };

        return Comparator.comparing(VerificationMessage::getSeverity)
                .thenComparing(VerificationMessage::getMessage)
                .thenComparing(VerificationMessage::getSubItems, subItemComparator)
                .compare(this, other);
    }

    @Override
    public boolean equals(Object other) {
        if(other instanceof VerificationMessage) {
            return compareTo((VerificationMessage)other) == 0;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hashCode = severity.hashCode();
        if(message != null) {
            hashCode += message.hashCode();
        }
        if(subItems != null) {
            hashCode += subItems.hashCode();
        }
        return hashCode;
    }

    public enum Severity {
        INFO(0, "INFO", "black"),
        WARNING(1, "WARNING", "orange"),
        REQUIRED(2, "REQUIRED", "red");

        private final String message;
        private final String color;
        private int level;

        Severity(int level, String message, String color) {
            this.level = level;
            this.message = message;
            this.color = color;
        }

        public String getMessage() {
            return message;
        }

        public String getColor() {
            return color;
        }

        public int getLevel() {
            return level;
        }
    }

    public String getMessage() {
        return message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public HashSet<VerificationMessage> getSubItems() {
        if(subItems == null) {
            subItems = new HashSet<VerificationMessage>();   
        }
        return subItems;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", severity.getMessage(), message);
    }
}
