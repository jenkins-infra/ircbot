package org.jenkinsci.backend.ircbot;

import java.util.ArrayList;
import java.util.List;

class RepoLabels {
    public static class Label {
        public final String name;
        public final String oldname;
        public final String color;
        public final String description;

        public Label() {
            name = "";
            oldname = "";
            color = "";
            description = "";
        }
    }

    public final List<Label> labels;

    public RepoLabels() {
        this.labels = new ArrayList<>();
    }
}
