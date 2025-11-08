package hw;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.PowerSource;

public final class BatteryMonitor {
    private static final SystemInfo SI = new SystemInfo();
    private static final HardwareAbstractionLayer HAL = SI.getHardware();

    private BatteryMonitor() {
    }

    public static boolean onAC() {
        String override = System.getProperty("forceOnAC");
        if (override != null)
            return Boolean.parseBoolean(override);

        try {
            PowerSource[] ps = HAL.getPowerSources().toArray(new PowerSource[0]);
            if (ps.length == 0)
                return true; // assume desktop/AC

            for (PowerSource p : ps) {
                if (p.isPowerOnLine())
                    return true;
            }
            return false;
        } catch (Throwable t) {
            return true; // fallback safe
        }
    }

    public static int levelOrGuess() {
        String lvl = System.getProperty("forceBatteryLevel");
        if (lvl != null) {
            try {
                int v = Integer.parseInt(lvl.trim());
                return Math.max(0, Math.min(100, v));
            } catch (Exception ignored) {
            }
        }

        try {
            PowerSource[] ps = HAL.getPowerSources().toArray(new PowerSource[0]);
            if (ps.length == 0)
                return 100;

            double sum = 0;
            int n = 0;
            for (PowerSource p : ps) {
                double pct = p.getRemainingCapacityPercent();
                if (!Double.isNaN(pct)) {
                    sum += pct;
                    n++;
                }
            }
            if (n == 0)
                return 100;
            return (int) Math.round((sum / n) * 100.0);
        } catch (Throwable t) {
            return 100;
        }
    }
}
