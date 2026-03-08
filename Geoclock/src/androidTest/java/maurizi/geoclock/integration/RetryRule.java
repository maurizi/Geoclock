package maurizi.geoclock.integration;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit rule that retries a failed test (including setUp/tearDown) up to {@code maxRetries} times.
 * Useful for tests that depend on external timing (e.g. geofence transitions from Play Services).
 */
public class RetryRule implements TestRule {
    private final int maxRetries;

    public RetryRule(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Throwable lastError = null;
                for (int i = 0; i <= maxRetries; i++) {
                    try {
                        base.evaluate();
                        return;
                    } catch (Throwable t) {
                        lastError = t;
                    }
                }
                throw lastError;
            }
        };
    }
}
