package hu.bme.prog3.factcalc.model;

import java.util.Objects;

public class SolverNotice {
    public enum Level {
        INFO,
        WARNING,
        ERROR
    }

    private Level level;
    private String message;

    /**
     * Creates an empty SolverNotice instance for deserialization.
     */
    public SolverNotice() {
    }

    /**
     * Creates a SolverNotice with a severity level and human-readable message.
     *
     * @param level   notice severity level
     * @param message descriptive message for the user
     */
    public SolverNotice(Level level, String message) {
        this.level = Objects.requireNonNull(level, "level must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
    }

    /**
     * Returns the severity level of this notice.
     *
     * @return the notice Level
     */
    public Level getLevel() {
        return level;
    }

    /**
     * Sets the severity level of this notice.
     *
     * @param level the notice Level
     */
    public void setLevel(Level level) {
        this.level = level;
    }

    /**
     * Returns the notice text message.
     *
     * @return the notice message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the notice text message.
     *
     * @param message the notice message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Formats the notice as {@code [LEVEL] message}.
     *
     * @return formatted notice string
     */
    @Override
    public String toString() {
        return "[" + level + "] " + message;
    }
}
