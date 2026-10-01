package lab.neurolab.brain;

/** Explicit experimental conditions; observation never writes movement commands. */
public enum ControlMode {
    ASSISTED, NEURAL, OBSERVE;

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public boolean controlsBody() { return this != OBSERVE; }
    public boolean noAi(boolean originalNoAi) { return controlsBody() || originalNoAi; }
}
