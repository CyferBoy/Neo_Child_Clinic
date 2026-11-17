package android.app;

/**
 * Test fake for KeyguardManager residing in android.app to access package-private constructor.
 */
public class FakeKeyguardManager extends KeyguardManager {
    public boolean locked = false;

    public FakeKeyguardManager() {
        super();
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean getLocked() {
        return this.locked;
    }

    public boolean isLocked() {
        return this.locked;
    }

    @Override
    public boolean isKeyguardLocked() {
        return this.locked;
    }
}
