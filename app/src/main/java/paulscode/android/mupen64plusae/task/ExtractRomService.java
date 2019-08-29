/*
 * Mupen64PlusAE, an N64 emulator for the Android platform
 * 
 * Copyright (C) 2013 Paul Lamb
 * 
 * This file is part of Mupen64PlusAE.
 * 
 * Mupen64PlusAE is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Mupen64PlusAE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Mupen64PlusAE. If
 * not, see <http://www.gnu.org/licenses/>.
 * 
 * Authors: fzurita
 */
package paulscode.android.mupen64plusae.task;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import androidx.core.app.NotificationCompat;
import android.util.Log;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import es.jdbc.n64retroplus.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import paulscode.android.mupen64plusae.ActivityHelper;
import paulscode.android.mupen64plusae.GalleryActivity;
import paulscode.android.mupen64plusae.dialog.ProgressDialog;
import paulscode.android.mupen64plusae.dialog.ProgressDialog.OnCancelListener;
import paulscode.android.mupen64plusae.util.FileUtil;
import paulscode.android.mupen64plusae.util.RomHeader;
import paulscode.android.mupen64plusae.util.SevenZInputStream;

public class ExtractRomService extends Service {
    private String mZipPath;
    private String mRomPath;
    private String mExtractZipPath;
    private String mMd5;

    private int mStartId;
    private ServiceHandler mServiceHandler;

    private final IBinder mBinder = new LocalBinder();
    private ExtractRomsListener mListener = null;

    final static int ONGOING_NOTIFICATION_ID = 1;

    final static String NOTIFICATION_CHANNEL_ID = "ExtractRomServiceChannel";
    final static String NOTIFICATION_CHANNEL_ID_V2 = "ExtractRomServiceChannelV2";

    public interface ExtractRomsListener {
        //This is called once the ROM scan is finished
        void onExtractRomFinished();

        //This is called when the service is destroyed
        void onExtractRomServiceDestroyed();

        //This is called to get a progress dialog object
        ProgressDialog GetProgressDialog();
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public ExtractRomService getService() {
            // Return this instance of ExtractRomService so clients can call public methods
            return ExtractRomService.this;
        }
    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {

            //Check for error conditions
            if (mZipPath == null) {
                if (mListener != null) {
                    mListener.onExtractRomFinished();
                }

                stopSelf(msg.arg1);
                return;
            }

            File zipPathFile = new File(mZipPath);

            if (!zipPathFile.exists()) {
                if (mListener != null) {
                    mListener.onExtractRomFinished();
                }

                stopSelf(msg.arg1);
                return;
            }

            final RomHeader romHeader = new RomHeader(mZipPath);

            if (romHeader.isZip) {
                ExtractZipFileIfNeeded(mMd5, mRomPath, mZipPath);
            } else if (romHeader.is7Zip) {
                ExtractSevenZFileIfNeeded(mMd5, mRomPath, mZipPath);
            }

            if (mListener != null) {
                mListener.onExtractRomFinished();
            }

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }

    private void ExtractZipFileIfNeeded(String md5, String romPath, String zipPath) {
        final File romFile = new File(romPath);
        String romFileName = romFile.getName();
        final File extractedRomFile = new File(mExtractZipPath + "/" + romFileName);

        if (!extractedRomFile.exists()) {
            boolean lbFound = false;

            try {
                final ZipFile zipFile = new ZipFile(zipPath);
                final Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements() && !lbFound) {
                    final ZipEntry zipEntry = entries.nextElement();

                    try {
                        final InputStream zipStream = zipFile.getInputStream(zipEntry);

                        final File destDir = new File(mExtractZipPath);
                        final String entryName = new File(zipEntry.getName()).getName();

                        lbFound = entryName.equals(romFileName);

                        if(entryName.equals(romFileName)) {
                            File tempRomPath = FileUtil.extractRomFile(destDir, zipEntry.getName(), zipStream);
                            Log.i("ExtractRomService", "Extracted zip entry: " + tempRomPath);
                        }

                        zipStream.close();
                    } catch (final IOException e) {
                        Log.w("ExtractRomService", e);
                    }
                }
                zipFile.close();
            } catch (final IOException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                Log.w("ExtractRomService", e);
            }
        }
    }

    private void ExtractSevenZFileIfNeeded(String md5, String romPath, String zipPath) {
        final File romFile = new File(romPath);
        String romFileName = romFile.getName();
        final File extractedRomFile = new File(mExtractZipPath + "/" + romFileName);

        if (!extractedRomFile.exists()) {
            boolean lbFound = false;

            try {
                SevenZFile zipFile = new SevenZFile(new File(zipPath));
                SevenZArchiveEntry zipEntry;

                while( (zipEntry = zipFile.getNextEntry()) != null && !lbFound)
                {
                    try {
                        final InputStream zipStream = new BufferedInputStream(new SevenZInputStream(zipFile));

                        final File destDir = new File(mExtractZipPath);
                        final String entryName = new File(zipEntry.getName()).getName();

                        lbFound = entryName.equals(romFileName);

                        if (entryName.equals(romFileName)) {
                            File tempRomPath = FileUtil.extractRomFile(destDir, zipEntry.getName(), zipStream);
                            Log.i("ExtractRomService", "Extracted zip entry: " + tempRomPath);
                        }

                        zipStream.close();
                    } catch (final IOException e) {
                        Log.w("ExtractRomService", e);
                    }
                }

                zipFile.close();
            } catch (final IOException | ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                Log.w("ExtractRomService", e);
            }
            catch (java.lang.OutOfMemoryError e)
            {
                Log.w( "CacheRomInfoService", "Out of memory while extracting 7zip entry: " + romFile.getPath() );
            }
        }
    }


    public void initChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_V2,
                    getString(R.string.extractRomTask_title), NotificationManager.IMPORTANCE_LOW);
            channel.enableVibration(false);
            channel.setSound(null,null);

            notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onCreate() {
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handle
        Looper serviceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(serviceLooper);

        //Show the notification
        initChannels(getApplicationContext());
        Intent notificationIntent = new Intent(this, GalleryActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_V2).setSmallIcon(R.drawable.icon)
                .setContentTitle(getString(R.string.extractRomTask_title))
                .setContentText(getString(R.string.toast_pleaseWait))
                .setContentIntent(pendingIntent);
        startForeground(ONGOING_NOTIFICATION_ID, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Bundle extras = intent.getExtras();
            mZipPath = extras.getString(ActivityHelper.Keys.ZIP_PATH);
            mExtractZipPath = extras.getString(ActivityHelper.Keys.EXTRACT_ZIP_PATH);
            mRomPath = extras.getString(ActivityHelper.Keys.ROM_PATH);
            mMd5 = extras.getString(ActivityHelper.Keys.ROM_MD5);
        }

        mStartId = startId;

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mListener != null) {
            mListener.onExtractRomServiceDestroyed();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void setExtractRomListener(ExtractRomsListener extractRomListener) {
        mListener = extractRomListener;
        mListener.GetProgressDialog().setOnCancelListener(new OnCancelListener() {
            @Override
            public void OnCancel() {

            }
        });

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = mStartId;
        mServiceHandler.sendMessage(msg);
    }
}
