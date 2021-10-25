/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.appcenter.distribute;

import static com.microsoft.appcenter.distribute.DistributeConstants.LOG_TAG;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.microsoft.appcenter.utils.AppCenterLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Installer utils.
 */
public class InstallerUtils {

    /**
     * Value when {@link Settings.Secure#INSTALL_NON_MARKET_APPS} setting is enabled.
     */
    @VisibleForTesting
    static final String INSTALL_NON_MARKET_APPS_ENABLED = "1";

    /**
     * Name of package installer stream.
     */
    private static final String sOutputStreamName = "AppCenterPackageInstallerStream";

    /**
     * Buffer capacity of package installer.
     */
    private static final int sBufferCapacity = 16384;

    /**
     * Installer package names that are not app stores.
     */
    private static final Set<String> LOCAL_STORES = new HashSet<>();

    /**
     * Used to cache the result of {@link #isInstalledFromAppStore(String, Context)}, null until first call.
     */
    private static Boolean sInstalledFromAppStore;

    /* Populate local stores. */
    static {
        LOCAL_STORES.add("adb");
        LOCAL_STORES.add("com.android.packageinstaller");
        LOCAL_STORES.add("com.google.android.packageinstaller");
        LOCAL_STORES.add("com.android.managedprovisioning");
        LOCAL_STORES.add("com.miui.packageinstaller");
        LOCAL_STORES.add("com.samsung.android.packageinstaller");
        LOCAL_STORES.add("pc");
        LOCAL_STORES.add("com.google.android.apps.nbu.files");
        LOCAL_STORES.add("org.mozilla.firefox");
        LOCAL_STORES.add("com.android.chrome");
        LOCAL_STORES.add("com.heytap.browser");
        LOCAL_STORES.add("com.coloros.filemanager");
        LOCAL_STORES.add("com.whatsapp");
        LOCAL_STORES.add("manual_install");
        LOCAL_STORES.add("com.coloros.browser");
        LOCAL_STORES.add("cn.xender");
        LOCAL_STORES.add("com.nearme.browser");
        LOCAL_STORES.add("com.android.browser");
        LOCAL_STORES.add("com.UCMobile.intl");
        LOCAL_STORES.add("com.gbwhatsapp");
        LOCAL_STORES.add("com.lenovo.anyshare.gps");
        LOCAL_STORES.add("com.sharekaro.app");
        LOCAL_STORES.add("com.opera.mini.native");
        LOCAL_STORES.add("com.xiaomi.midrop");
        LOCAL_STORES.add("com.opera.browser");
        LOCAL_STORES.add("sharefiles.sharemusic.shareapps.filetransfer");
        LOCAL_STORES.add("com.coloros.backuprestore");
        LOCAL_STORES.add("com.nemo.vidmate");
        LOCAL_STORES.add("com.uc.browser.en");
        LOCAL_STORES.add("com.android.packageinstaller");
        LOCAL_STORES.add("com.vivo.easyshare");
        LOCAL_STORES.add("com.sec.android.easyMover");
        LOCAL_STORES.add("com.android.providers.downloads");
        LOCAL_STORES.add("com.Slack");
        LOCAL_STORES.add("com.tapnav.karma");
        LOCAL_STORES.add("com.antivirus");
        LOCAL_STORES.add("share.indiashare.appshare");
        LOCAL_STORES.add("com.android.bluetooth");
        LOCAL_STORES.add("com.brave.browser");
        LOCAL_STORES.add("share.zender.appshare");
        LOCAL_STORES.add("com.oasisfeng.island");
        LOCAL_STORES.add("com.miui.cloudbackup");
        LOCAL_STORES.add("com.mi.global.mimover");
        LOCAL_STORES.add("com.fmwhatsapp");
        LOCAL_STORES.add("share.sharekaro.pro");
        LOCAL_STORES.add("com.oneplus.backuprestore");
        LOCAL_STORES.add("com.reliance.jio.jioswitch");
        LOCAL_STORES.add("com.chrome.dev");
        LOCAL_STORES.add("org.telegram.messenger");
        LOCAL_STORES.add("com.futuredial.asusdatatransfer");
        LOCAL_STORES.add("com.blueWAplus");
        LOCAL_STORES.add("com.azip.unrar.unzip.extractfile");
        LOCAL_STORES.add("com.mi.globalbrowser.mini");
        LOCAL_STORES.add("com.jio.web");
        LOCAL_STORES.add("com.apkinstaller.ApkInstaller");
        LOCAL_STORES.add("com.infinix.xshare");
        LOCAL_STORES.add("com.browser.tssomas");
        LOCAL_STORES.add("com.mxtech.videoplayer.share");
        LOCAL_STORES.add("com.aero");
        LOCAL_STORES.add("com.opera.browser.beta");
        LOCAL_STORES.add("com.dewmobile.kuaiya.play");
        LOCAL_STORES.add("com.avast.android.mobilesecurity");
        LOCAL_STORES.add("com.samsung.android.scloud");
        LOCAL_STORES.add("com.mi.android.globalFileexplorer");
        LOCAL_STORES.add("freevpn.supervpn.video.downloader");
        LOCAL_STORES.add("me.weishu.exp");
    }

    @VisibleForTesting
    InstallerUtils() {

        /* Hide constructor in utils pattern. */
    }

    /**
     * Check if this installation was made via an application store.
     *
     * @param logTag  log tag for debug.
     * @param context any context.
     * @return true if the application was installed from an app store, false if it was installed via adb or via unknown source.
     */
    static synchronized boolean isInstalledFromAppStore(@NonNull String logTag, @NonNull Context context) {
        if (sInstalledFromAppStore == null) {
            String installer = context.getPackageManager().getInstallerPackageName(context.getPackageName());
            AppCenterLog.debug(logTag, "InstallerPackageName=" + installer);
            sInstalledFromAppStore = installer != null && !LOCAL_STORES.contains(installer);
        }
        return sInstalledFromAppStore;
    }

    /**
     * Add new stores to local stores list.
     *
     * @param stores list of stores.
     */
    public static void addLocalStores(Set<String> stores) {
        LOCAL_STORES.addAll(stores);
    }

    /**
     * Check whether user enabled installation via unknown sources.
     *
     * @param context any context.
     * @return true if installation via unknown sources is enabled, false otherwise.
     */
    @SuppressWarnings("deprecation")
    public static boolean isUnknownSourcesEnabled(@NonNull Context context) {

        /*
         * On Android 8 with applications targeting lower versions,
         * it's impossible to check unknown sources enabled: using old APIs will always return true
         * and using the new one will always return false,
         * so in order to avoid a stuck dialog that can't be bypassed we will assume true.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.O || context.getPackageManager().canRequestPackageInstalls();
        } else {
            return INSTALL_NON_MARKET_APPS_ENABLED.equals(Settings.Secure.getString(context.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS));
        }
    }

    /**
     * Install new release.
     * @param data input stream data from the install apk.
     */
    public static void installPackage(InputStream data, Context context, ReleaseInstallerListener releaseInstallerListener) throws IOException {
        PackageInstaller.Session session = null;
        try {

            /* Prepare package installer. */
            PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();
            if (releaseInstallerListener != null) {
                packageInstaller.registerSessionCallback(releaseInstallerListener);
            }
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);

            /* Prepare session. */
            int sessionId = packageInstaller.createSession(params);
            session = packageInstaller.openSession(sessionId);

            /* Start installing. */
            OutputStream out = session.openWrite(sOutputStreamName, 0, -1);
            byte[] buffer = new byte[sBufferCapacity];
            int c;
            while ((c = data.read(buffer)) != -1) {
                out.write(buffer, 0, c);
            }
            session.fsync(out);
            data.close();
            out.close();
            session.commit(createIntentSender(context, sessionId));
        } catch (IOException e) {
            AppCenterLog.error(LOG_TAG, "Couldn't install package", e);
        } catch (RuntimeException e) {
            if (session != null) {
                session.abandon();
            }
            AppCenterLog.error(LOG_TAG, "Couldn't install package", e);
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    /**
     * Return IntentSender with the receiver that will be launched after installation.
     *
     * @param context any context.
     * @param sessionId install sessionId.
     * @return IntentSender with receiver.
     */
    public static IntentSender createIntentSender(Context context, int sessionId) {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                new Intent(AppCenterPackageInstallerReceiver.START_ACTION),
                0);
        return pendingIntent.getIntentSender();
    }
}
