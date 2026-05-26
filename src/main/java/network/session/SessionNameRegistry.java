package network.session;

import java.util.HashSet;
import java.util.Set;

public class SessionNameRegistry {
    private final Set<String> reservedNames = new HashSet<>();
    private final Set<String> committedNames = new HashSet<>();

    public synchronized boolean reserve(String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) {
            throw new IllegalArgumentException("normalizedName is required");
        }
        if (committedNames.contains(normalizedName) || reservedNames.contains(normalizedName)) {
            return false;
        }
        reservedNames.add(normalizedName);
        return true;
    }

    public synchronized boolean commit(String normalizedName) {
        if (!reservedNames.remove(normalizedName)) {
            return false;
        }
        committedNames.add(normalizedName);
        return true;
    }

    public synchronized boolean release(String normalizedName) {
        return reservedNames.remove(normalizedName);
    }

    public synchronized boolean isCommitted(String normalizedName) {
        return committedNames.contains(normalizedName);
    }

    /** Removes a committed name when a player disconnects from the session. */
    public synchronized boolean unregister(String normalizedName) {
        boolean removedCommitted = committedNames.remove(normalizedName);
        reservedNames.remove(normalizedName);
        return removedCommitted;
    }
}
