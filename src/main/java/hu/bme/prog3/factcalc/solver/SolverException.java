package hu.bme.prog3.factcalc.solver;

public class SolverException extends RuntimeException {
    /**
     * Creates a SolverException with a descriptive user-facing error message.
     *
     * @param message explanation of the calculation or validation failure
     */
    public SolverException(String message) {
        super(message);
    }

    /**
     * Creates a SolverException with an error message and underlying cause.
     *
     * @param message explanation of the failure
     * @param cause   underlying exception cause
     */
    public SolverException(String message, Throwable cause) {
        super(message, cause);
    }
}
