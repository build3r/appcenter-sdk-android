/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.appcenter.distribute.download.manager;

import static com.microsoft.appcenter.distribute.DistributeConstants.LOG_TAG;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;

import androidx.annotation.VisibleForTesting;

import com.microsoft.appcenter.distribute.ReleaseDetails;
import com.microsoft.appcenter.utils.AppCenterLog;

/**
 * The download manager API triggers strict mode exception in UI thread.
 */
class DownloadManagerRequestTask extends AsyncTask<Void, Void, Void> {

    private final DownloadManagerReleaseDownloader mDownloader;
    private final String mTitle;
    private final Context mContext;

    DownloadManagerRequestTask(Context context, DownloadManagerReleaseDownloader downloader, String title) {
        mContext = context;
        mDownloader = downloader;
        mTitle = title;
    }

    @Override
    protected Void doInBackground(Void... params) {

        /* Download file. */
        ReleaseDetails releaseDetails = mDownloader.getReleaseDetails();
        Uri downloadUrl = releaseDetails.getDownloadUrl();
        AppCenterLog.debug(LOG_TAG, "Start downloading new release from " + downloadUrl);
        DownloadManager downloadManager = mDownloader.getDownloadManager();
        DownloadManager.Request request = createRequest(downloadUrl);
        request.setTitle(String.format(mTitle, releaseDetails.getShortVersion(), releaseDetails.getVersion()));

        /* Hide mandatory download to prevent canceling via notification cancel or download UI delete. */
        if (releaseDetails.isMandatoryUpdate()) {
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
            request.setVisibleInDownloadsUi(false);
        }
        request.setDestinationInExternalFilesDir(mContext, Environment.DIRECTORY_DOWNLOADS, "app.apk");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        request.setVisibleInDownloadsUi(true);
        request.setDescription("Downloading Update");// Description of the Download Notification

        long enqueueTime = System.currentTimeMillis();
        try {
            long downloadId = downloadManager.enqueue(request);
            if (!isCancelled()) {
                mDownloader.onDownloadStarted(downloadId, enqueueTime);
            }
        } catch (IllegalArgumentException e) {
            
            /*
             * In cases when Download Manager application is disabled,
             * IllegalArgumentException: Unknown URL content://downloads/my_download is thrown.
             */
            mDownloader.onDownloadError(new IllegalStateException("Failed to start download: Download Manager is disabled.", e));
        }
        return null;
    }

    @VisibleForTesting
    DownloadManager.Request createRequest(Uri Uri) {
        return new DownloadManager.Request(Uri);
    }
}
