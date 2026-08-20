package com.leosprojects.busdisplay;

/** Pure-Java transition guard for profiles with a one-shot stopping sound. */
public final class EngineMotionState {
    public enum State { MOVING, STOPPING, STOPPED }
    public enum Action { NONE, START_MOVING, START_STOPPING }

    private static final int MOVEMENT_REARM_KMH = 3;
    private State state = State.STOPPED;
    private boolean stoppingArmed;

    public Action onSpeedKmh(int speedKmh) {
        if (speedKmh >= MOVEMENT_REARM_KMH) {
            if (state != State.MOVING) {
                state = State.MOVING;
                stoppingArmed = true;
                return Action.START_MOVING;
            }
            stoppingArmed = true;
            return Action.NONE;
        }
        if (speedKmh == 0 && state == State.MOVING && stoppingArmed) {
            state = State.STOPPING;
            stoppingArmed = false;
            return Action.START_STOPPING;
        }
        return Action.NONE;
    }

    public void completeStopping() {
        if (state == State.STOPPING) state = State.STOPPED;
    }

    public void reset(int speedKmh) {
        state = speedKmh >= MOVEMENT_REARM_KMH ? State.MOVING : State.STOPPED;
        stoppingArmed = state == State.MOVING;
    }

    public int continuousLoopBand(int movingBand, boolean hasStoppedLoop) {
        if (state == State.MOVING) return movingBand;
        if (state == State.STOPPED && hasStoppedLoop) return 0;
        return -1;
    }

    public State state() { return state; }
}
