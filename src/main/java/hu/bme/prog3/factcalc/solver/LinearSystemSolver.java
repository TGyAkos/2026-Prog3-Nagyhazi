package hu.bme.prog3.factcalc.solver;

/**
 * Dense linear system solver (Ax = b) using Gaussian elimination with partial pivoting.
 */
public final class LinearSystemSolver {

    public static final double EPS = 1e-9;

    /**
     * Private constructor to prevent instantiation of utility solver class.
     */
    private LinearSystemSolver() {
    }

    /**
     * Solves the linear system {@code A * x = b} for an {@code n x n} square matrix using
     * Gaussian elimination with partial pivoting.
     *
     * @param a {@code n x n} coefficient matrix
     * @param b right-hand-side demand vector of length {@code n}
     * @return solution vector {@code x} of length {@code n}
     * @throws IllegalArgumentException if {@code a} is not a square matrix matching {@code b.length}
     * @throws SolverException          if the system is singular or contains an unresolvable loop
     */
    public static double[] solve(double[][] a, double[] b) {
        int n = b.length;
        if (n == 0) {
            return new double[0];
        }
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            if (a[i].length != n) {
                throw new IllegalArgumentException("Matrix must be square");
            }
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }

        for (int col = 0; col < n; col++) {
            int pivotRow = col;
            double maxAbs = Math.abs(m[col][col]);
            for (int row = col + 1; row < n; row++) {
                double val = Math.abs(m[row][col]);
                if (val > maxAbs) {
                    maxAbs = val;
                    pivotRow = row;
                }
            }

            if (maxAbs < 1e-11) {
                throw new SolverException("The recipe dependency system is singular or contains an unresolvable loop.");
            }

            if (pivotRow != col) {
                double[] tmp = m[col];
                m[col] = m[pivotRow];
                m[pivotRow] = tmp;
            }

            double pivot = m[col][col];
            for (int row = col + 1; row < n; row++) {
                double factor = m[row][col] / pivot;
                if (Math.abs(factor) < 1e-15) {
                    continue;
                }
                m[row][col] = 0.0;
                for (int k = col + 1; k <= n; k++) {
                    m[row][k] -= factor * m[col][k];
                }
            }
        }

        double[] x = new double[n];
        for (int row = n - 1; row >= 0; row--) {
            double sum = m[row][n];
            for (int col = row + 1; col < n; col++) {
                sum -= m[row][col] * x[col];
            }
            x[row] = sum / m[row][row];
            if (Math.abs(x[row]) < EPS) {
                x[row] = 0.0;
            }
        }
        return x;
    }
}
