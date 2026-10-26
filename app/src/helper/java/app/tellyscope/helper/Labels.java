// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.helper;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs on the TV as the shell user, through {@code app_process}, and prints one JSON object per
 * app: {@code {"p":package,"l":label,"v":versionCode,"i":base64 PNG}}. An app with only a TV
 * banner gets {@code "b"} and {@code "a"} in place of {@code "i"}: its letter tile's colours as
 * ARGB. Arguments are the packages to describe, or {@code --all}. It only reads through
 * PackageManager: no writes, no permissions, no network. The app pushes it, runs it once and deletes it.
 */
public final class Labels {
    private static final int ICON_PX = 192;
    /** Enough of a banner to find its colours; a 16:9 banner is drawn at this width. */
    private static final int BANNER_PX = 160;

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        PackageManager pm = systemContext().getPackageManager();
        PrintStream out = new PrintStream(System.out, false, "UTF-8");

        List<String> names = new ArrayList<>();
        if (args.length == 1 && args[0].equals("--all")) {
            for (PackageInfo p : pm.getInstalledPackages(0)) names.add(p.packageName);
        } else {
            for (String a : args) names.add(a);
        }
        for (String name : names) {
            // The whole line is built before any of it is printed: a throw halfway used to leave an
            // unclosed object that glued itself to the next app's line, and the app lost both.
            try {
                PackageInfo p = pm.getPackageInfo(name, 0);
                ApplicationInfo ai = p.applicationInfo;
                if (ai == null) continue;
                StringBuilder line = new StringBuilder("{\"p\":");
                string(line, name);
                line.append(",\"l\":");
                string(line, ai.loadLabel(pm).toString());
                line.append(",\"v\":").append(versionCode(p));
                int[] tile = !ownIcon(ai.icon) && ai.banner != 0 ? tile(pm, ai) : null;
                String icon = tile == null ? icon(pm, ai) : null;
                if (tile != null) {
                    line.append(",\"b\":").append(tile[0] & 0xffffffffL).append(",\"a\":").append(tile[1] & 0xffffffffL);
                } else if (icon != null) {
                    line.append(",\"i\":\"").append(icon).append('"');
                }
                line.append("}\n");
                out.print(line);
            } catch (Throwable t) {
                // One unreadable app (gone since the list, broken resources, a drawable that runs the
                // helper out of memory) doesn't stop the rest.
            }
        }
        out.flush();
    }

    /** No Context in a bare app_process; build the system one the way the framework does for itself. */
    private static Context systemContext() throws Exception {
        Class<?> at = Class.forName("android.app.ActivityThread");
        java.lang.reflect.Constructor<?> ctor = at.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object thread = ctor.newInstance();
        Field current = at.getDeclaredField("sCurrentActivityThread");
        current.setAccessible(true);
        current.set(null, thread);
        Method get = at.getDeclaredMethod("getSystemContext");
        Context system = (Context) get.invoke(thread);
        // Adaptive icons inflate against ActivityThread.currentApplication(), which a bare app_process
        // doesn't have: without one every adaptive icon throws and the app falls back to its banner.
        // If this fails on some Android, that fallback is still what happens; it's logged once.
        try {
            Application app = new Application();
            Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            attach.setAccessible(true);
            attach.invoke(app, system);
            Field initial = at.getDeclaredField("mInitialApplication");
            initial.setAccessible(true);
            initial.set(thread, app);
        } catch (Throwable t) {
            Log.w("Tellyscope", "no Application for adaptive icons", t);
        }
        return system;
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo p) {
        return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
    }

    /**
     * The first artwork that loads and draws: the icon, then the banner. An app with no icon of its
     * own would draw as the generic robot, so it sends only its banner, or nothing for a letter tile.
     */
    private static String icon(PackageManager pm, ApplicationInfo ai) {
        int[] kinds = ownIcon(ai.icon) ? new int[] {ICON, BANNER} : new int[] {BANNER};
        for (int kind : kinds) {
            try {
                String png = render(art(pm, ai, kind));
                if (png != null) return png;
            } catch (Throwable t) {
                // Try the next one; the label is still worth sending without any.
            }
        }
        return null;
    }

    private static final int ICON = 0, BANNER = 1;

    /** False when an app's icon resource is none, or the framework's default one. */
    public static boolean ownIcon(int icon) {
        return icon != 0 && icon != android.R.drawable.sym_def_app_icon && icon != android.R.mipmap.sym_def_app_icon;
    }

    /**
     * A banner shrunk into a square icon is a thin strip of logo, so a banner-only app gets a letter
     * tile in the banner's colours instead. Null when the banner doesn't draw: then [icon] tries.
     */
    private static int[] tile(PackageManager pm, ApplicationInfo ai) {
        try {
            Drawable d = ai.loadBanner(pm);
            if (d == null) return null;
            int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
            int bh = w > 0 && h > 0 ? Math.max(1, BANNER_PX * h / w) : BANNER_PX * 9 / 16;
            Bitmap b = Bitmap.createBitmap(BANNER_PX, Math.min(bh, BANNER_PX), Bitmap.Config.ARGB_8888);
            try {
                d.setBounds(0, 0, b.getWidth(), b.getHeight());
                d.draw(new Canvas(b));
                int[] pixels = new int[b.getWidth() * b.getHeight()];
                b.getPixels(pixels, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
                return BannerColors.pick(pixels);
            } finally {
                b.recycle();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static Drawable art(PackageManager pm, ApplicationInfo ai, int kind) {
        switch (kind) {
            case ICON: return ai.loadIcon(pm);
            default: return ai.loadBanner(pm); // null when the app has none
        }
    }

    private static String render(Drawable d) {
        if (d == null) return null;
        Bitmap b = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888);
        try {
            if (Build.VERSION.SDK_INT >= 26 && fullBleed(d, new Canvas(b))) return png(b);
            // Fit, centred: a 16:9 banner keeps its shape instead of being squashed square.
            int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
            if (w > 0 && h > 0 && w != h) {
                int fw = w > h ? ICON_PX : ICON_PX * w / h;
                int fh = w > h ? ICON_PX * h / w : ICON_PX;
                d.setBounds((ICON_PX - fw) / 2, (ICON_PX - fh) / 2, (ICON_PX + fw) / 2, (ICON_PX + fh) / 2);
            } else {
                d.setBounds(0, 0, ICON_PX, ICON_PX);
            }
            d.draw(new Canvas(b));
            return png(b);
        } finally {
            b.recycle();
        }
    }

    /**
     * An adaptive icon drawn as its layers, square and unmasked: the TV's own mask (circle, squircle,
     * whatever the maker chose) would differ per TV, so the app clips every icon to one shape instead.
     * A layer is 108 dp of which the middle 72 dp is the icon, so it overhangs the square by a quarter.
     */
    private static boolean fullBleed(Drawable d, Canvas canvas) {
        if (!(d instanceof AdaptiveIconDrawable)) return false;
        AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) d;
        int bleed = ICON_PX / 4;
        for (Drawable layer : new Drawable[] {adaptive.getBackground(), adaptive.getForeground()}) {
            if (layer == null) continue;
            layer.setBounds(-bleed, -bleed, ICON_PX + bleed, ICON_PX + bleed);
            layer.draw(canvas);
        }
        return true;
    }

    private static String png(Bitmap b) {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, png);
        return Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP);
    }

    /** JSON string, ASCII only: anything outside printable ASCII is a \\u escape. */
    private static void string(StringBuilder out, String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\');
                out.append(c);
            } else if (c < 0x20 || c > 0x7e) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }
}
