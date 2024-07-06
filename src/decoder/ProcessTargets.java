/*
 * Public Domain (p) 2024 Steve Sampson, K5OKC
 */
package decoder;

import parser.ZuluMillis;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public final class ProcessTargets {

    private final ZuluMillis zulu;
    //
    private final static long RATE = 1500L;                         // 1.5 second
    public final static long TRACK_DROPTIME = 3L * 60L * 1000L;     // 3 Minutes
    //
    private final ConcurrentHashMap<String, Target> targets;
    private final Timer timer;
    private final TimerTask task;
    private boolean EOF;

    public ProcessTargets() {
        zulu = new ZuluMillis();
        EOF = false;
        targets = new ConcurrentHashMap<>();

        task = new DropTarget();
        timer = new Timer();
        timer.scheduleAtFixedRate(task, 0L, RATE);
    }

    public void close() {
        EOF = true;
        timer.cancel();
    }

    public synchronized boolean hasTarget(String acid) throws NullPointerException {
        try {
            return targets.containsKey(acid);
        } catch (NullPointerException e) {
            throw new NullPointerException("ProcessTargets::hasTarget Exception during containsKey " + e.toString());
        }
    }

    public synchronized int getQueueSize() {
        return targets.size();
    }

    public synchronized Target getTarget(String acid) throws NullPointerException {
        try {
            return targets.get(acid);
        } catch (NullPointerException e) {
            throw new NullPointerException("ProcessTargets::getTarget Exception during get " + e.toString());
        }
    }

    /**
     * Method to return a collection of all targets.
     *
     * @return a vector Representing all target objects.
     * @throws java.lang.Exception
     */
    public synchronized List<Target> getAllTargets() throws Exception {
        List<Target> result = new ArrayList<>();

        try {
            targets.values().stream().forEach((obj) -> {
                result.add(obj);
            });

            return result;
        } catch (Exception e) {
            throw new Exception("ProcessTargets::getAllTargets Exception during add " + e.toString());
        }
    }

    /**
     * Find all the updated targets, and reset them to not updated for the next
     * pass
     *
     * @return a vector representing all the targets that have been updated
     * @throws java.lang.Exception
     */
    public synchronized List<Target> getAllUpdatedTargets() throws Exception {
        List<Target> result = new ArrayList<>();

        try {
            targets.values().stream().forEach((tgt) -> {
                if (tgt.getUpdated() == true) {
                    tgt.setUpdated(false);
                    tgt.setUpdatedTime(zulu.getUTCTime());

                    targets.put(tgt.getAcid(), tgt);     // overwrites original target
                    result.add(tgt);
                }
            });

            return result;
        } catch (Exception e) {
            throw new Exception("ProcessTargets::getAllUpdatedTargets Exception during add " + e.toString());
        }
    }

    /**
     * After the Target object is created by DataBlockParser, we come here and put the
     * target on the queue. This is also used to overwrite a target.
     *
     * @param acid a String representing the Aircraft ID
     * @param obj an Object representing the Target data
     */
    public synchronized void addTarget(String acid, Target obj) throws NullPointerException {
        try {
            targets.put(acid, obj);
        } catch (NullPointerException e) {
            throw new NullPointerException("ProcessTargets::addTarget Exception during put " + e.toString());
        }
    }

    public synchronized void removeTarget(String acid) throws NullPointerException {
        try {
            if (targets.containsKey(acid) == true) {
                targets.remove(acid);
            }
        } catch (NullPointerException e) {
            throw new NullPointerException("ProcessTargets::removeTarget Exception " + e.toString());
        }
    }

    /**
     * This is the DROP position timer
     *
     * <p>
     * It drops old targets, site ID's, and TCAS alerts
     */
    private final class DropTarget extends TimerTask {

        private long delta;
        private long currentTime;
        private long targetTime;
        private List<Target> targets;

        @Override
        public void run() {
            if (EOF == true) {
                return;
            }

            currentTime = zulu.getUTCTime();

            try {
                targets = getAllTargets();
            } catch (Exception te) {
                return; // No targets found
            }

            for (Target obj : targets) {
                try {
                    targetTime = obj.getUpdatedTime();

                    if (targetTime != 0L) {
                        delta = Math.abs(currentTime - targetTime); // abs() in case of wierdness

                        if (delta >= TRACK_DROPTIME) {
                            removeTarget(obj.getAcid());
                        }
                    }
                } catch (NullPointerException e2) {
                    // ignore
                }

                if (obj.hasTCASAlerts()) {
                    /*
                     * Remove TCAS Alerts older than X minutes
                     */
                    obj.removeTCAS(currentTime);
                }
            }
        }
    }
}
