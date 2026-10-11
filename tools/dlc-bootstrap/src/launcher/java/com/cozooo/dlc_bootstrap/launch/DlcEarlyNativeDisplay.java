package com.cozooo.dlc_bootstrap.launch;

//? if >=1.20.1 <1.21.1 {
/*import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.progress.ProgressMeter;
import net.minecraftforge.fml.loading.progress.StartupNotificationManager;

final class DlcEarlyNativeDisplay implements DlcEarlyProgress.Display {
    private final Runnable tick;
    private ProgressMeter meter;
    private int steps = -1;

    private DlcEarlyNativeDisplay(Runnable tick) {
        this.tick = tick;
    }

    static DlcEarlyProgress.Display open() {
        if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) return null;
        if (!"fmlearlywindow".equals(FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER))) return null;
        Runnable tick = FMLLoader.progressWindowTick;
        return tick == null ? null : new DlcEarlyNativeDisplay(tick);
    }

    @Override public void update(DlcEarlyProgress.Frame frame) {
        if (meter == null || steps != frame.steps()) {
            close();
            meter = StartupNotificationManager.addProgressBar("DLC Manager", frame.steps());
            steps = frame.steps();
        }
        meter.label(frame.label());
        meter.setAbsolute(frame.current());
    }

    @Override public void tick() {
        tick.run();
    }

    @Override public void close() {
        ProgressMeter old = meter;
        meter = null;
        if (old != null) old.complete();
    }
}
*///?}
//? if >=1.21.1 <26.1 {
/*import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

final class DlcEarlyNativeDisplay implements DlcEarlyProgress.Display {
    private final Runnable tick;
    private ProgressMeter meter;
    private int steps = -1;

    private DlcEarlyNativeDisplay(Runnable tick) {
        this.tick = tick;
    }

    static DlcEarlyProgress.Display open() {
        if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) return null;
        if (!"fmlearlywindow".equals(FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER))) return null;
        Runnable tick = FMLLoader.progressWindowTick;
        return tick == null ? null : new DlcEarlyNativeDisplay(tick);
    }

    @Override public void update(DlcEarlyProgress.Frame frame) {
        if (meter == null || steps != frame.steps()) {
            close();
            meter = StartupNotificationManager.addProgressBar("DLC Manager", frame.steps());
            steps = frame.steps();
        }
        meter.label(frame.label());
        meter.setAbsolute(frame.current());
    }

    @Override public void tick() {
        tick.run();
    }

    @Override public void close() {
        ProgressMeter old = meter;
        meter = null;
        if (old != null) old.complete();
    }
}
*///?}
//? if >=26.1 {
/*import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;

final class DlcEarlyNativeDisplay implements DlcEarlyProgress.Display {
    private ProgressMeter meter;
    private int steps = -1;

    private DlcEarlyNativeDisplay() {}

    static DlcEarlyProgress.Display open() {
        if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_CONTROL)) return null;
        if (!"fmlearlywindow".equals(FMLConfig.getConfigValue(FMLConfig.ConfigValue.EARLY_WINDOW_PROVIDER))) return null;
        return new DlcEarlyNativeDisplay();
    }

    @Override public void update(DlcEarlyProgress.Frame frame) {
        if (meter == null || steps != frame.steps()) {
            close();
            meter = StartupNotificationManager.prependProgressBar("DLC Manager", frame.steps());
            steps = frame.steps();
        }
        meter.label(frame.label());
        meter.setAbsolute(frame.current());
    }

    @Override public void tick() {}

    @Override public void close() {
        ProgressMeter old = meter;
        meter = null;
        if (old != null) old.complete();
    }
}
*///?}
//? if <1.20.1 {
/*final class DlcEarlyNativeDisplay {
    private DlcEarlyNativeDisplay() {}

    static DlcEarlyProgress.Display open() {
        return null;
    }
}
*///?}
