package de.danoeh.antennapod.shufflepod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A followed person (guest or host). Immutable; use {@link People} to change one.
 */
public final class Person {
    private final long id;
    private final String name;
    private final List<String> aliases;
    private final boolean inPool;
    private final long since;

    /**
     * @param since only episodes published at or after this time (epoch millis) are collected;
     *              0 collects past appearances too
     */
    public Person(long id, String name, List<String> aliases, boolean inPool, long since) {
        this.id = id;
        this.name = name;
        this.aliases = Collections.unmodifiableList(new ArrayList<>(aliases));
        this.inPool = inPool;
        this.since = since;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getAliases() {
        return aliases;
    }

    /**
     * The name plus all aliases, i.e. every spelling to match on.
     */
    public List<String> getNames() {
        List<String> names = new ArrayList<>();
        names.add(name);
        for (String alias : aliases) {
            if (!names.contains(alias)) {
                names.add(alias);
            }
        }
        return names;
    }

    public boolean isInPool() {
        return inPool;
    }

    public long getSince() {
        return since;
    }

    public boolean includesPublishDate(long publishedAtMillis) {
        return since <= 0 || publishedAtMillis >= since;
    }

    Person withInPool(boolean newInPool) {
        return new Person(id, name, aliases, newInPool, since);
    }

    Person withSince(long newSince) {
        return new Person(id, name, aliases, inPool, newSince);
    }

    Person withNames(String newName, List<String> newAliases) {
        return new Person(id, newName, newAliases, inPool, since);
    }
}
