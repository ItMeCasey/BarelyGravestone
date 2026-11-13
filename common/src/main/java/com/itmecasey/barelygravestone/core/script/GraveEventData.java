package com.itmecasey.barelygravestone.core.script;

/**
 * The event objects handed to script handlers. They hold only plain values and a few methods, so any script engine can use them.
 * In a handler: {@code event.grave().ownerName()}, {@code event.cancel()}.
 */
public final class GraveEventData {
    private GraveEventData() {
    }

    /** Base: the grave the event is about. */
    public abstract static class Base {
        private final GraveView grave;

        Base(GraveView grave) {
            this.grave = grave;
        }

        public GraveView grave() {
            return grave;
        }
    }

    /** Before a grave is made. Call {@link #cancel()} and the items drop normally instead; the XP price is not taken either. */
    public static final class BeforeCreate extends Base {
        private boolean cancelled;

        public BeforeCreate(GraveView grave) {
            super(grave);
        }

        public void cancel() {
            cancelled = true;
        }

        public boolean cancelled() {
            return cancelled;
        }
    }

    /** After a grave was made. */
    public static final class Created extends Base {
        public Created(GraveView grave) {
            super(grave);
        }
    }

    /**
     * A player is about to collect a grave. {@link #allowed()} is what the options decided; call {@link #allow()} or
     * {@link #deny()} (or {@link #cancel()}, the same as deny) to change it.
     */
    public static final class BeforeCollect extends Base {
        private final String collectorId;
        private final String collectorName;
        private boolean allowed;

        public BeforeCollect(GraveView grave, String collectorId, String collectorName, boolean allowed) {
            super(grave);
            this.collectorId = collectorId;
            this.collectorName = collectorName;
            this.allowed = allowed;
        }

        public String collectorId() {
            return collectorId;
        }

        public String collectorName() {
            return collectorName;
        }

        /** Whether the player is the grave's owner. */
        public boolean isOwner() {
            return collectorId.equals(grave().ownerId());
        }

        public boolean allowed() {
            return allowed;
        }

        public void allow() {
            allowed = true;
        }

        public void deny() {
            allowed = false;
        }

        public void cancel() {
            allowed = false;
        }
    }

    /** After a player collected from a grave. */
    public static final class Collected extends Base {
        private final String collectorId;
        private final String collectorName;
        private final int stacksLeft;

        public Collected(GraveView grave, String collectorId, String collectorName, int stacksLeft) {
            super(grave);
            this.collectorId = collectorId;
            this.collectorName = collectorName;
            this.stacksLeft = stacksLeft;
        }

        public String collectorId() {
            return collectorId;
        }

        public String collectorName() {
            return collectorName;
        }

        /** How many stacks stayed in the grave (the inventory was full). */
        public int stacksLeft() {
            return stacksLeft;
        }

        /** Whether the grave is empty and gone now. */
        public boolean emptied() {
            return stacksLeft == 0;
        }
    }

    /** The expiry timer ran out. Call {@link #cancel()} and the grave stays and its timer starts again. */
    public static final class Expiring extends Base {
        private boolean cancelled;

        public Expiring(GraveView grave) {
            super(grave);
        }

        public void cancel() {
            cancelled = true;
        }

        public boolean cancelled() {
            return cancelled;
        }
    }

    /** After a grave expired and dropped its items. */
    public static final class Expired extends Base {
        public Expired(GraveView grave) {
            super(grave);
        }
    }
}
