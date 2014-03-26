/*
 * Copyright (C) 2013 Parallel Universe Software Co.
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package co.paralleluniverse.webspaceships;

import co.paralleluniverse.actors.ActorRef;
import co.paralleluniverse.actors.BasicActor;
import co.paralleluniverse.actors.MailboxConfig;
import co.paralleluniverse.comsat.webactors.WebDataMessage;
import co.paralleluniverse.data.record.Field;
import co.paralleluniverse.data.record.Record;
import co.paralleluniverse.db.record.StrandedTransactionalRecord;
import co.paralleluniverse.db.record.TransactionalRecord;
import co.paralleluniverse.fibers.*;
import co.paralleluniverse.spacebase.AABB;
import static co.paralleluniverse.spacebase.AABB.X;
import static co.paralleluniverse.spacebase.AABB.Y;
import co.paralleluniverse.spacebase.MutableAABB;
import co.paralleluniverse.spacebase.SpatialQueries;
import co.paralleluniverse.spacebase.SpatialToken;
import co.paralleluniverse.spacebase.quasar.Element;
import co.paralleluniverse.spacebase.quasar.ElementUpdater1;
import co.paralleluniverse.spacebase.quasar.ResultSet;
import static co.paralleluniverse.webspaceships.SpaceshipState.*;
import co.paralleluniverse.strands.channels.Channels;
import co.paralleluniverse.strands.concurrent.Phaser;
import static java.lang.Math.*;
import java.text.DecimalFormat;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A spaceship
 */
public class Spaceship extends BasicActor<Object, Void> {
    public static enum Status {
        ALIVE, BLOWING_UP, GONE
    };
    private static final int MIN_PERIOD_MILLIS = 30;
    private static final int MAX_SEARCH_RANGE = 400;
    private static final int MAX_SEARCH_RANGE_SQUARED = MAX_SEARCH_RANGE * MAX_SEARCH_RANGE;
    private static final int TIMES_HIT_TO_BLOW = 9;
    private static final double REJECTION_COEFF = 80000.0;
    private static final double SPEED_LIMIT = 100.0;
    private static final double SPEED_BOUNCE_DAMPING = 0.9;
    private static final double MIN_PROXIMITY = 4;
    private static final double HIT_RECOIL_VELOCITY = -100.0;
    private static final int BLAST_RANGE = 200;
    private static final int BLOW_TILL_DELETE_DURATION = 1000;
    private static final double SEARCH_PROBABLITY = 0.02;
    private static final int SHOOT_INABILITY_DURATION = 3000;
    private static final int SHOOT_RANGE = 200;
    private static final int SHOOT_ACCURACY = 10;
    private static final double SHOOT_PROBABLITY = 0.1;
    public static final int MAX_SPACESHIPS_IN_MSG = 20;
    //
    private final Spaceships global;
    private final RandSpatial random;
    private final int id;
    private Record<SpaceshipState> state; // the ships' public state - explanation below
    private final Queue<DelayedRunnable> delayQueue = new PriorityQueue<DelayedRunnable>();
    private final Phaser phaser;
    // private state:
    private Status status = Status.ALIVE;
    private SpatialToken lockedOn;
    private double chaseAx;
    private double chaseAy;
    private double ax;
    private double ay;
    private double exVx = 0;
    private double exVy = 0;
    private int timesHit = 0;
    private long timeHit = 0;
    private long timeFired = 0;
    private double shotLength = 10f;
    // "external velocity" does not result from thruster (but from nearby explosions or by getting hit), and threfore does not affect heading
    private long exVelocityUpdated = 0;
    private double turn;
    private double throttle;
    private ActorRef<WebDataMessage> client;
    private long lastSent;

    // The public state is only updated by the owning Spaceship, and only in a SB transaction.
    // Therefore the owning spaceship can read it any time, but anyone else (other spacehips or the renderer) must only do so in
    // a transaction.
    public Spaceship(Spaceships global, int id, Phaser phaser) {
        this(global, id, phaser, null);
    }

    Spaceship(Spaceships global, int id, Phaser phaser, ActorRef<WebDataMessage> client) {
        super(new MailboxConfig(10, Channels.OverflowPolicy.THROW));
        this.id = id;
        this.state = SpaceshipState.stateType.newInstance();
        this.phaser = phaser;
        this.client = client;

        this.global = global;
        this.random = global.random;
        state.set($x, random.randRange(global.bounds.min(X), global.bounds.max(X)));
        state.set($y, random.randRange(global.bounds.min(Y), global.bounds.max(Y)));

        final double direction = random.nextDouble() * 2 * Math.PI;
        final double speed = SPEED_LIMIT / 4 + random.nextGaussian() * global.speedVariance;
        setVelocityDir(direction, speed);
    }

    @Override
    public String toString() {
        return "Spaceship@" + Integer.toHexString(System.identityHashCode(this)) + '(' + id + ')';
    }

    @Override
    protected Void doRun() throws InterruptedException, SuspendExecution {
        if (phaser != null)
            phaser.register();
        boolean deregistered = false;
        long lastSent = 0;
        try {
            state.set($spaceship, ref());
            state.set($token, global.sb.insert(new TransactionalRecord<>(this, state), getAABB()));
            this.state = new StrandedTransactionalRecord<>(state, true, global.sb); // protect state

            record(1, "Spaceship", "doRun", "%s", this);

            for (int i = 0;; i++) {
                final long nextCycle = status == Status.ALIVE
                        ? Math.min(state.get($lastMoved) + MIN_PERIOD_MILLIS, nextActionTime())
                        : nextActionTime();
                Object message = receive(nextCycle - now(), TimeUnit.MILLISECONDS);
                final long now = now();
                if (message != null) {
                    // handle message
                    if (message instanceof WebDataMessage) {
                        WebDataMessage wsm = (WebDataMessage) message;
                        String dataString = wsm.getStringBody();
                        if (dataString.startsWith("ping")) {
                            client.send(new WebDataMessage(ref(), "pong," + dataString.substring("ping ".length()) + "," + global.getN() + ","
                                    + global.getControlledCount()));
                        } else {
                            switch (dataString) {
                                case "subscribe":
                                    break;
                                case "fire":
                                    tryShoot();
                                    break;
                                case "right":
                                    this.turn = Math.min(3.0, turn + 1.0);
                                    break;
                                case "left":
                                    this.turn = Math.max(-3.0, turn - 1.0);
                                    break;
                                case "up":
                                    this.throttle = Math.min(3.0, throttle + 1.0);
                                    break;
                                case "down":
                                    this.throttle = Math.max(-3.0, throttle - 1.0);
                                    break;
                                case "exit":
                                    blow(now);
                                    break;
                            }
                            sendUpdateToClient(now, true);
                        }
                    } else if (message instanceof Shot) {
                        boolean killed = shot(now, ((Shot) message).x, ((Shot) message).y);

                        if (killed && client != null)
                            client.send(new WebDataMessage(ref(), "killed"));
                        if (killed && phaser != null) {

                            deregistered = true;
                            phaser.arriveAndDeregister();
                        }
                    } else if (message instanceof Blast)
                        blast(now, ((Blast) message).x, ((Blast) message).y);
                } else {
                    // no message
                    runDelayed(now); // apply delayed actions
                    sendUpdateToClient(now, false);

                    if (status == Status.GONE) {
                        record(1, "Spaceship", "doRun", "%s: gone", this);
                        return null;
                    } else if (status == Status.ALIVE) {
                        if (client == null) {
                            if (!isLockedOnTarget()) {
                                if (canFight(now) && wantToFight(now))
                                    searchForTargets();
                            } else
                                chaseAndShoot();
                        }
                        applyNeighborRejectionAndMove(now);
                    }

                    global.spaceshipsCycles.inc();

                    if (phaser != null)
                        phaser.arriveAndAwaitAdvance();
                }
                if (isRecordingLevel(1))
                    record(1, "Spaceship", "doRun", "%s: iter %s", this, i);
            }
        } catch (Throwable e) {
            System.err.println("Exception in spaceship: " + this);
            e.printStackTrace();
            return null;
        } finally {
            record(1, "Spaceship", "doRun", "%s: DONE", this);
            if (phaser != null && !deregistered)
                phaser.arriveAndDeregister();
            global.sb.delete(state.get($token));
        }
    }

    private boolean canFight(long now) {
        return now - timeHit > SHOOT_INABILITY_DURATION;
    }

    private boolean wantToFight(long now) {
        return random.nextFloat() < SEARCH_PROBABLITY;
    }

    private void searchForTargets() throws SuspendExecution, InterruptedException {
        record(1, "Spaceship", "searchForTargets", "%s: searching...", this);

        try (ResultSet<Record<SpaceshipState>> rs = global.sb.query(new RadarQuery(state.get($x), state.get($y), state.get($vx), state.get($vy), toRadians(30), MAX_SEARCH_RANGE))) {
            Record<SpaceshipState> nearestShip = null;
            double minRange2 = MAX_SEARCH_RANGE_SQUARED;
            for (Record<SpaceshipState> s : rs.getResultReadOnly()) {
                final double dx = s.get($x) - state.get($x);
                final double dy = s.get($y) - state.get($y);

                final double rng2 = dx * dx + dy * dy;
                if (rng2 > 100 & rng2 <= minRange2) { //not me and not so close
                    minRange2 = rng2;
                    nearestShip = s;
                }
            }
            if (nearestShip != null)
                lockOnTarget(nearestShip);
            record(1, "Spaceship", "searchForTargets", "%s: size of radar query: %d", this, rs.getResultReadOnly().size());
        }
    }

    private void tryShoot() throws SuspendExecution, InterruptedException {
        try (ResultSet<Record<SpaceshipState>> rs = global.sb.query(new RadarQuery(state.get($x), state.get($y), state.get($vx), state.get($vy), toRadians(10), SHOOT_RANGE))) {
            Record<SpaceshipState> nearestShip = null;
            double minRange2 = SHOOT_RANGE * SHOOT_RANGE;
            for (Record<SpaceshipState> s : rs.getResultReadOnly()) {
                final double dx = s.get($x) - state.get($x);
                final double dy = s.get($y) - state.get($y);

                final double rng2 = dx * dx + dy * dy;
                if (rng2 > 100 & rng2 <= minRange2) { //not me and not so close
                    minRange2 = rng2;
                    nearestShip = s;
                }
            }
            if (nearestShip != null) {
                shoot(Math.sqrt(minRange2));
                nearestShip.get($spaceship).send(new Shot(state.get($x), state.get($y)));
            } else {
                shoot(SHOOT_RANGE);
            }
        }
    }

    private void chaseAndShoot() throws SuspendExecution, InterruptedException {
        record(1, "Spaceship", "chaseAndShoot", "%s: locked", this);
        // check lock range, chase, shoot
        boolean foundLockedOn = false;
        try (Element<Record<SpaceshipState>> target = global.sb.readElement(lockedOn)) {
            final Record<SpaceshipState> lockedSpaceship;
            if (target != null && (lockedSpaceship = target.get()) != null) {
                foundLockedOn = true;

                final AABB aabb = getAABB(lockedSpaceship);
                // double angularDiversion = abs(atan2(lockedSpaceship.vx, lockedSpaceship.vy) - getCurrentHeading(shootTime));
                if (inShotRange(aabb) & global.random.nextGaussian() < SHOOT_PROBABLITY) {
                    record(1, "Spaceship", "chaseAndShoot", "%s: shootrange", this);
                    final double range = mag(lockedSpaceship.get($x) - state.get($x), lockedSpaceship.get($y) - state.get($y));
                    shoot(range);
                    lockedSpaceship.get($spaceship).send(new Shot(state.get($x), state.get($y)));
                }
                if (inLockRange(aabb)) {
                    record(1, "Spaceship", "chaseAndShoot", "%s: lockrange", this);
                    chase(lockedSpaceship);
                } else {
                    record(1, "Spaceship", "chaseAndShoot", "%s: release lock", this);
                    lockOnTarget(null);  // not in range, release lock
                }
            }
        }
        if (!foundLockedOn)
            lockOnTarget(null);
    }

    private boolean inLockRange(AABB aabb) {
        return new RadarQuery(state.get($x), state.get($y), state.get($vx), state.get($vy), toRadians(30), MAX_SEARCH_RANGE).queryElement(aabb, null);
    }

    private boolean inShotRange(AABB aabb) {
        final double v = mag(state.get($vx), state.get($vy));
        final double x2 = state.get($x) + state.get($vx) / v * SHOOT_RANGE;
        final double y2 = state.get($y) + state.get($vy) / v * SHOOT_RANGE;
        return (new LineDistanceQuery<Spaceship>(state.get($x) + 1, x2, state.get($y) + 1, y2, SHOOT_ACCURACY).queryElement(aabb, this));
    }

    /**
     * Accelerate toward given ship
     */
    private void chase(Record<SpaceshipState> target) {
        final double dx = target.get($x) - state.get($x);
        final double dy = target.get($y) - state.get($y);
        double d = mag(dx, dy);
        if (d < MIN_PROXIMITY)
            d = MIN_PROXIMITY;
        final double udx = dx / d;
        final double udy = dy / d;
        final double acc = 200;
        chaseAx = acc * udx;
        chaseAy = acc * udy;
    }

    private void applyNeighborRejectionAndMove(final long now) throws InterruptedException, SuspendExecution {
        AABB myAABB = getAABB();

        try (ResultSet<Record<SpaceshipState>> rs = global.sb.query(SpatialQueries.range(myAABB, global.range))) {
            applyNeighborRejection(rs.getResultReadOnly(), now);
        }
        try (ElementUpdater1<Record<SpaceshipState>> updater = global.sb.update(state.get($token))) {
            state.set($ax, ax);
            state.set($ay, ay);

            move(now);
            state.set($status, status);
            state.set($timeFired, timeFired);
            state.set($shotLength, shotLength);
            state.set($exVelocityUpdated, exVelocityUpdated);
            updater.update(getAABB());
        }
        reduceExternalVelocity(now);
    }

    // called in a transaction
    private void applyNeighborRejection(Set<Record<SpaceshipState>> neighbors, long currentTime) {
        final int n = neighbors.size();

        this.ax = chaseAx;
        this.ay = chaseAy;

        if (n > 1) {
            for (Record<SpaceshipState> s : neighbors) {
                if (s == this.state)
                    continue;

                assert !Double.isNaN(state.get($x) + state.get($y));

                final double dx = s.get($x) - state.get($x);
                final double dy = s.get($y) - state.get($y);
                double d = mag(dx, dy);
                if (d < MIN_PROXIMITY)
                    d = MIN_PROXIMITY;

                final double udx = dx / d;
                final double udy = dy / d;

                double rejection = min(REJECTION_COEFF / (d * d), 250);

                this.ax = ax - rejection * udx;
                this.ay = ay - rejection * udy;

                if (Double.isNaN(ax + ay))
                    assert false;
            }
        }
    }

    /**
     * Update ship position
     */
    private void move(long now) {
        assert status == Status.ALIVE;

        state.set($exVx, exVx);
        state.set($exVy, exVy);

        final long lastMoved = state.get($lastMoved);

        if (lastMoved > 0 & now > lastMoved) {
            if (client != null) {
                if (Math.abs(turn) > 1e-4) { // controlled from webactor
                    double mag = Math.max(mag(state.get($vx), state.get($vy)), 2);
                    double dir = Math.atan2(state.get($vy), state.get($vx));
                    dir -= (Math.PI / 180.0) * 5.0 * turn;
                    state.set($vy, mag * Math.sin(dir));
                    state.set($vx, mag * Math.cos(dir));
                    turn *= 0.8;
                }
                if (Math.abs(throttle) > 1e-4) {
                    double mag = Math.max(mag(state.get($vx), state.get($vy)), 2);
                    double newMag = Math.max(1, Math.min(SPEED_LIMIT, mag + 10.0 * throttle));
                    state.set($vx, state.get($vx) * newMag / mag);
                    state.set($vy, state.get($vy) * newMag / mag);
                    throttle *= 0.8;
                }
            }

            double x = state.get($x);
            double y = state.get($y);
            double vx = state.get($vx);
            double vy = state.get($vy);
            double ax = state.get($ax);
            double ay = state.get($ay);

            final AABB bounds = global.bounds;
            final double duration = (double) (now - lastMoved) / TimeUnit.SECONDS.toMillis(1);
            final double duration2 = duration * duration / 2.0;// * Math.signum(duration);

            x = x + (vx + exVx) * duration + ax * duration2;
            y = y + (vy + exVy) * duration + ay * duration2;

            vx = vx + ax * duration;
            vy = vy + ay * duration;

            // before limitSpeed
            state.set($vx, vx);
            state.set($vy, vy);
            limitSpeed();
            vx = state.get($vx);
            vy = state.get($vy);

            assert !Double.isNaN(vx + vy);

            if (x > bounds.max(X) || x < bounds.min(X)) {
                x = min(x, bounds.max(X));
                x = max(x, bounds.min(X));
                vx = -vx * SPEED_BOUNCE_DAMPING;
                exVx = -exVx * SPEED_BOUNCE_DAMPING;
                ax = 0;
                turn = 0;
            }
            if (y > bounds.max(Y) || y < bounds.min(Y)) {
                y = min(y, bounds.max(Y));
                y = max(y, bounds.min(Y));
                vy = -vy * SPEED_BOUNCE_DAMPING;
                exVy = -exVy * SPEED_BOUNCE_DAMPING;
                ay = 0;
                turn = 0;
            }

            assert !Double.isNaN(x + y);

            state.set($x, x);
            state.set($y, y);
            state.set($vx, vx);
            state.set($vy, vy);
            state.set($ax, ax);
            state.set($ay, ay);
            state.set($exVx, exVx);
            state.set($exVy, exVy);
        }
        state.set($lastMoved, now);
    }

    /**
     * I've been shot!
     *
     * @param global
     * @param shooter
     */
    private boolean shot(long now, double shooterX, double shooterY) throws SuspendExecution, InterruptedException {
        record(1, "Spaceship", "shot", "%s: shot", this);
        this.timesHit++;
        timeHit = now;
        if (timesHit < TIMES_HIT_TO_BLOW) {
            final double dx = shooterX - state.get($x);
            final double dy = shooterY - state.get($y);
            double d = mag(dx, dy);
            if (d < MIN_PROXIMITY)
                d = MIN_PROXIMITY;
            final double udx = dx / d;
            final double udy = dy / d;

            reduceExternalVelocity(now);
            exVx += HIT_RECOIL_VELOCITY * udx;
            exVy += HIT_RECOIL_VELOCITY * udy;
            this.exVelocityUpdated = now;
            return false;
        } else if (status == Status.ALIVE) {
            blow(now);
            return true;
        }
        return false;
    }

    void blow(long now) throws SuspendExecution, InterruptedException {
        // System.out.println("BOOM: " + this);
        record(1, "Spaceship", "shot", "%s: BOOM", this);
        // I'm dead: blow up. The explosion pushes away all nearby ships.
        try (ResultSet<Record<SpaceshipState>> rs = global.sb.query(SpatialQueries.range(getAABB(), BLAST_RANGE))) {
            final Blast blastMessage = new Blast(now(), state.get($x), state.get($y));
            for (Record<SpaceshipState> s : rs.getResultReadOnly())
                s.get($spaceship).send(blastMessage);
        }
        this.status = Status.BLOWING_UP;

        try (ElementUpdater1<Record<SpaceshipState>> up = global.sb.update(state.get($token))) {
            state.set($status, Status.BLOWING_UP);
            state.set($vx, 0.0);
            state.set($vy, 0.0);
            state.set($exVx, 0.0);
            state.set($exVy, 0.0);
            state.set($ax, 0.0);
            state.set($ay, 0.0);
            state.set($blowTime, now);
        }

        delay(now, BLOW_TILL_DELETE_DURATION, TimeUnit.MILLISECONDS, new Runnable() {
            public void run() {
                status = Status.GONE;
            }
        });
    }

    private boolean isLockedOnTarget() {
        return lockedOn != null;
    }

    private void lockOnTarget(Record<SpaceshipState> target) {
        if (target != null)
            lockedOn = target.get($token);
        else
            lockedOn = null;
        chaseAx = 0;
        chaseAy = 0;
    }

    private void shoot(double range) {
        timeFired = global.now();
        shotLength = range;
    }

    /**
     * A nearby ship has exploded, accelerate away in the blast.
     */
    private void blast(long now, double explosionX, double explosionY) {
        final double dx = explosionX - state.get($x);
        final double dy = explosionY - state.get($y);
        final double d = mag(dx, dy);
        if (d < MIN_PROXIMITY)
            return;
        final double udx = dx / d;
        final double udy = dy / d;

        double hitRecoil = 0.25 * d - 200;

        reduceExternalVelocity(now);
        exVx += hitRecoil * udx;
        exVy += hitRecoil * udy;
        this.exVelocityUpdated = now;
    }

    private void setVelocityDir(double direction, double speed) {
        state.set($vx, speed * cos(direction));
        state.set($vy, speed * sin(direction));
        limitSpeed();
    }

    private void limitSpeed() {
        final double vx = state.get($vx);
        final double vy = state.get($vy);

        final double speed = mag(vx, vy);
        if (speed > SPEED_LIMIT) {
            state.set($vx, vx / speed * SPEED_LIMIT);
            state.set($vy, vy / speed * SPEED_LIMIT);
        }
    }

    private void reduceExternalVelocity(long currentTime) {
        double duration = (double) (currentTime - exVelocityUpdated) / TimeUnit.SECONDS.toMillis(1);
        if (exVelocityUpdated > 0 & duration > 0) {
            exVx /= (1 + 8 * duration);
            exVy /= (1 + 8 * duration);
        }
        exVelocityUpdated = currentTime;
    }

    private AABB getAABB() {
        final MutableAABB aabb = AABB.create(2);
        getAABB(aabb);
        return aabb;
    }

    private void getAABB(MutableAABB aabb) {
        // capture x and y atomically (each)
        final double _x = state.get($x);
        final double _y = state.get($y);
        aabb.min(X, _x);
        aabb.max(X, _x);
        aabb.min(Y, _y);
        aabb.max(Y, _y);
    }

    private static AABB getAABB(Record<SpaceshipState> state) {
        final MutableAABB aabb = AABB.create(2);
        getAABB(state, aabb);
        return aabb;
    }

    private static void getAABB(Record<SpaceshipState> state, MutableAABB aabb) {
        // capture x and y atomically (each)
        final double _x = state.get($x);
        final double _y = state.get($y);
        aabb.min(X, _x);
        aabb.max(X, _x);
        aabb.min(Y, _y);
        aabb.max(Y, _y);
    }

    private double mag(double x, double y) {
        return sqrt(x * x + y * y);
    }

    private long now() {
        return global.now();
    }

    private void delay(long now, long delay, TimeUnit unit, final Runnable command) {
        delayQueue.add(new DelayedRunnable(now + unit.toMillis(delay)) {
            @Override
            public void run() {
                command.run();
            }
        });
    }

    private void runDelayed(long now) {
        for (;;) {
            DelayedRunnable command = delayQueue.peek();
            if (command != null && command.time <= now) {
                delayQueue.poll();
                command.run();
            } else
                break;
        }
    }

    private long nextActionTime() {
        DelayedRunnable command = delayQueue.peek();
        return command != null ? command.time : Long.MAX_VALUE;
    }

    void sendUpdateToClient(final long now, boolean forceSend) throws InterruptedException, SuspendExecution {
        if (client != null && status == Status.ALIVE && (forceSend || (now - lastSent > 100))) {
            this.lastSent = now;
            final String jsonMessage = getJsonMessage(now);
            // timesHit = 0;
            // System.out.println("sending: " + jsonMessage);
            client.send(new WebDataMessage(ref(), jsonMessage));
        }
    }

    private final static DecimalFormat df = new DecimalFormat("#.###");

    private String getJsonMessage(long now) throws InterruptedException, SuspendExecution {
        return "{"
                + "now:" + now + ","
                + "life:" + (TIMES_HIT_TO_BLOW - timesHit) + ","
                + "self:" + getSingleShipJson(state) + ","
                + "others:" + getSightRangeJson()
                + "}";
    }

    private String getSightRangeJson() throws InterruptedException, SuspendExecution {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
//        try (ResultSet<Record<SpaceshipState>> rs = global.sb.query(new RadarQuery(state.x, state.y, state.vx, state.vy, toRadians(60), MAX_SEARCH_RANGE * 2))) {
        try (ResultSet<Record<SpaceshipState>> rs = global.sb.query(SpatialQueries.range(getAABB(), MAX_SEARCH_RANGE * 2))) {
            int i = 0;
            for (Record<SpaceshipState> s : rs.getResultReadOnly()) {
                if (++i > MAX_SPACESHIPS_IN_MSG)
                    break;
                if (s.get($token) != state.get($token))
                    sb.append(getSingleShipJson(s)).append(',');
            }
        }
        if (sb.length() > 1)
            sb.deleteCharAt(sb.length() - 1);
        sb.append(']');
        return sb.toString();
    }

    private static String getSingleShipJson(Record<SpaceshipState> s) {
        return makeJson(s, s.get($spaceship).getName(), $x, $y, $vx, $vy, $ax, $ay, $exVx, $exVy, $exVelocityUpdated, $timeFired, $shotLength, $blowTime);
    }

    private static <R> String makeJson(Record<R> r, String name, Field<R, ?>... fields) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("name:\"").append(name).append('\"').append(',');
        for (Field<R, ?> f : fields) {
            final Object get = r.get(f);
            String value = get instanceof Double ? df.format(get) : get.toString();
            sb.append(f.name()).append(':').append(value).append(',');
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append('}');
        return sb.toString();
    }

    static class Shot {
        final double x;
        final double y;

        public Shot(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    static class Blast {
        final long time;
        final double x;
        final double y;

        public Blast(long time, double x, double y) {
            this.time = time;
            this.x = x;
            this.y = y;
        }
    }

    static abstract class DelayedRunnable implements Runnable, Comparable<DelayedRunnable> {
        final long time;

        public DelayedRunnable(long time) {
            this.time = time;
        }

        @Override
        public int compareTo(DelayedRunnable o) {
            return Long.signum(this.time - o.time);
        }
    }
}
