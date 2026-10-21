package de.mel.auth.data;

/**
 * The server's last word in a registration. True: it has persisted the registration. The client persists and closes.
 */
public class RegisterFinal extends ServicePayload {
    private Boolean accepted;

    public RegisterFinal() {
    }

    public RegisterFinal(boolean accepted) {
        this.accepted = accepted;
    }

    public boolean isAccepted() {
        return accepted != null && accepted;
    }
}
