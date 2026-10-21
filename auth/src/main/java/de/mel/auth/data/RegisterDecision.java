package de.mel.auth.data;

/**
 * The decision of the user of the sender in a registration.
 */
public class RegisterDecision extends ServicePayload {
    private Boolean accepted;

    public RegisterDecision() {
    }

    public RegisterDecision(boolean accepted) {
        this.accepted = accepted;
    }

    public boolean isAccepted() {
        return accepted != null && accepted;
    }
}
