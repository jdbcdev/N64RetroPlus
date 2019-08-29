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
 * Authors: littleguy77
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
import android.text.TextUtils;
import android.util.Log;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import es.jdbc.n64retroplus.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import paulscode.android.mupen64plusae.ActivityHelper;
import paulscode.android.mupen64plusae.GalleryActivity;
import paulscode.android.mupen64plusae.dialog.ProgressDialog;
import paulscode.android.mupen64plusae.dialog.ProgressDialog.OnCancelListener;
import paulscode.android.mupen64plusae.persistent.ConfigFile;
import paulscode.android.mupen64plusae.util.CountryCode;
import paulscode.android.mupen64plusae.util.FileUtil;
import paulscode.android.mupen64plusae.util.RomDatabase;
import paulscode.android.mupen64plusae.util.RomDatabase.RomDetail;
import paulscode.android.mupen64plusae.util.RomHeader;
import paulscode.android.mupen64plusae.util.SevenZInputStream;

public class CacheRomInfoService extends Service
{
    private String mSearchPath;
    private String mDatabasePath;
    private String mConfigPath;
    private String mArtDir;
    private String mUnzipDir;
    private boolean mSearchZips;
    private boolean mDownloadArt;
    private boolean mClearGallery;
    private boolean mSearchSubdirectories;
    private boolean mbStopped;
    
    private int mStartId;
    private ServiceHandler mServiceHandler;
    
    private final IBinder mBinder = new LocalBinder();
    private CacheRomInfoListener mListener = null;

    final static int ONGOING_NOTIFICATION_ID = 1;

    final static String NOTIFICATION_CHANNEL_ID = "CacheRomInfoServiceChannel";
    final static String NOTIFICATION_CHANNEL_ID_V2 = "CacheRomInfoServiceChannelV2";
    
    public interface CacheRomInfoListener
    {
        //This is called once the ROM scan is finished
        void onCacheRomInfoFinished();
        
        //This is called when the service is destroyed
        void onCacheRomInfoServiceDestroyed();
        
        //This is called to get a progress dialog object
        ProgressDialog GetProgressDialog();
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public CacheRomInfoService getService() {
            // Return this instance of CacheRomInfoService so clients can call public methods
            return CacheRomInfoService.this;
        }
    }

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }
        
        @Override
        public void handleMessage(Message msg) {

            File searchPathFile = new File(mSearchPath);

            if( mSearchPath == null )
                throw new IllegalArgumentException( "Root path cannot be null" );
            if( TextUtils.isEmpty( mDatabasePath ) )
                throw new IllegalArgumentException( "ROM database path cannot be null or empty" );
            if( TextUtils.isEmpty( mConfigPath ) )
                throw new IllegalArgumentException( "Config file path cannot be null or empty" );
            if( TextUtils.isEmpty( mArtDir ) )
                throw new IllegalArgumentException( "Art directory cannot be null or empty" );
            if( TextUtils.isEmpty( mUnzipDir ) )
                throw new IllegalArgumentException( "Unzip directory cannot be null or empty" );

            // Ensure destination directories exist
            FileUtil.makeDirs(mArtDir);
            FileUtil.makeDirs(mUnzipDir);

            // Create .nomedia file to hide cover art from Android Photo Gallery
            // http://android2know.blogspot.com/2013/01/create-nomedia-file.html
            touchFile( mArtDir + "/.nomedia" );
            
            final List<File> files = getAllFiles( searchPathFile, 0 );
            final RomDatabase database = RomDatabase.getInstance();
            if(!database.hasDatabaseFile())
            {
                database.setDatabaseFile(mDatabasePath);
            }
            
            final ConfigFile config = new ConfigFile( mConfigPath );
            if (mClearGallery)
                config.clear();
            
            mListener.GetProgressDialog().setMaxProgress( files.size() );
            for( final File file : files )
            {
                mListener.GetProgressDialog().setSubtext( "" );
                mListener.GetProgressDialog().setText( file.getName() );
                mListener.GetProgressDialog().setMessage( R.string.cacheRomInfo_searching );
                
                if( mbStopped ) break;
                RomHeader header = new RomHeader( file );
                if( header.isValid ) {
                    cacheFile( file, database, config);
                } else if (mSearchZips && !ConfigHasZip(config, file.getPath())) {
                    if (header.isZip) {
                        cacheZip(database, file, config);
                    } else if (header.is7Zip) {
                        cache7Zip(database, file, config);
                    }
                }

                mListener.GetProgressDialog().incrementProgress( 1 );
            }

            CleanupMissingFiles(config);
            downloadCoverArt(database, config);

            config.save();
            
            if (mListener != null)
            {
                mListener.onCacheRomInfoFinished();
            }

            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }

    public void initChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_V2,
                getString(R.string.scanning_title), NotificationManager.IMPORTANCE_LOW);
        channel.enableVibration(false);
        channel.setSound(null,null);

        if(notificationManager != null) {
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

      // Get the HandlerThread's Looper and use it for our Handler
      Looper serviceLooper;
      serviceLooper = thread.getLooper();
      mServiceHandler = new ServiceHandler(serviceLooper);

      //Show the notification
      initChannels(getApplicationContext());
      Intent notificationIntent = new Intent(this, GalleryActivity.class);
      PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
      NotificationCompat.Builder builder =
          new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_V2).setSmallIcon(R.drawable.icon)
          .setContentTitle(getString(R.string.scanning_title))
          .setContentText(getString(R.string.toast_pleaseWait))
          .setContentIntent(pendingIntent);
      startForeground(ONGOING_NOTIFICATION_ID, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null)
        {
            Bundle extras = intent.getExtras();

            if(extras == null)
            {
                throw new IllegalArgumentException("Invalid parameters passed to CacheRomInfoService");
            }
            mSearchPath = extras.getString( ActivityHelper.Keys.SEARCH_PATH );
            mDatabasePath = extras.getString( ActivityHelper.Keys.DATABASE_PATH );
            mConfigPath = extras.getString( ActivityHelper.Keys.CONFIG_PATH );
            mArtDir = extras.getString( ActivityHelper.Keys.ART_DIR );
            mUnzipDir = extras.getString( ActivityHelper.Keys.UNZIP_DIR );
            mSearchZips = extras.getBoolean( ActivityHelper.Keys.SEARCH_ZIPS );
            mDownloadArt = extras.getBoolean( ActivityHelper.Keys.DOWNLOAD_ART );
            mClearGallery = extras.getBoolean( ActivityHelper.Keys.CLEAR_GALLERY );
            mSearchSubdirectories = extras.getBoolean( ActivityHelper.Keys.SEARCH_SUBDIR );
        }

        mbStopped = false;
        mStartId = startId;

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }

    /**
     * Get all files in a directory and subdirectories
     * @param searchPath Path to start search on
     * @param count How many levels deep we currently are
     * @return List of files
     */
    private List<File> getAllFiles( File searchPath, int count )
    {
        List<File> result = new ArrayList<>();
        if( searchPath.isDirectory())
        {
            File[] allFiles = searchPath.listFiles();
            if(allFiles != null)
            {
                for( File file : allFiles )
                {
                    if( mbStopped ) break;

                    //Search subdirectories if option is enabled and we less than 10 levels deep
                    if(mSearchSubdirectories && count < 10)
                    {
                        result.addAll( getAllFiles( file, ++count ) );
                    }
                    else if(!file.isDirectory())
                    {
                        result.add(file);
                    }

                }
            }
        }
        else
        {
            result.add( searchPath );
        }
        return result;
    }

    private void cacheZip(RomDatabase database, File file, ConfigFile config)
    {
        Log.i( "CacheRomInfoService", "Found zip file " + file.getName() );
        try
        {
            ZipFile zipFile = new ZipFile( file );
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while( entries.hasMoreElements() && !mbStopped)
            {
                try
                {
                    ZipEntry zipEntry = entries.nextElement();
                    mListener.GetProgressDialog().setSubtext( new File(zipEntry.getName()).getName() );
                    mListener.GetProgressDialog().setMessage( R.string.cacheRomInfo_searchingZip );

                    InputStream zipStream = new BufferedInputStream(zipFile.getInputStream( zipEntry ));
                    mListener.GetProgressDialog().setMessage( R.string.cacheRomInfo_extractingZip );

                    cacheFileFromInputStream(database, file, config, new File(zipEntry.getName()).getName(),
                            zipStream);

                    zipStream.close();
                }
                catch( IOException|NoSuchAlgorithmException|IllegalArgumentException e  )
                {
                    Log.w( "CacheRomInfoService", e );
                }
            }
            zipFile.close();
        }
        catch( IOException|ArrayIndexOutOfBoundsException|java.lang.NullPointerException e )
        {
            Log.w( "CacheRomInfoService", e );
        }
    }

    private void cache7Zip(RomDatabase database, File file, ConfigFile config)
    {
        Log.i( "CacheRomInfoService", "Found 7zip file " + file.getName() );

        try
        {
            SevenZFile zipFile = new SevenZFile( file );
            SevenZArchiveEntry zipEntry;
            while( (zipEntry = zipFile.getNextEntry()) != null && !mbStopped)
            {
                try
                {
                    mListener.GetProgressDialog().setSubtext( new File(zipEntry.getName()).getName() );
                    mListener.GetProgressDialog().setMessage( R.string.cacheRomInfo_searchingZip );

                    InputStream zipStream = new BufferedInputStream(new SevenZInputStream(zipFile));
                    mListener.GetProgressDialog().setMessage( R.string.cacheRomInfo_extractingZip );

                    cacheFileFromInputStream(database, file, config, new File(zipEntry.getName()).getName(),
                            zipStream);

                    zipStream.close();
                }
                catch( IOException|NoSuchAlgorithmException |IllegalArgumentException e  )
                {
                    Log.w( "CacheRomInfoService", e );
                }
            }
            zipFile.close();
        }
        catch(IOException e)
        {
            Log.w( "CacheRomInfoService", "IOException: " + e );
        }
        catch (java.lang.OutOfMemoryError e)
        {
            Log.w( "CacheRomInfoService", "Out of memory while extracting 7zip entry: " + file.getPath() );
        }
    }

    private void cacheFileFromInputStream(RomDatabase database, File file, ConfigFile config, String name,
                                          InputStream inputStream) throws IOException, NoSuchAlgorithmException {
        //First get the rom header
        inputStream.mark(500);
        byte[] romHeader = FileUtil.extractRomHeader(inputStream);
        RomHeader extractedHeader;
        if(romHeader != null) {
            extractedHeader = new RomHeader( romHeader );

            if(extractedHeader.isValid)
            {
                Log.i( "FileUtil", "Found ROM entry " + name);

                //Then extract the ROM file
                inputStream.reset();

                String extractedFile = mUnzipDir + "/" + name;
                String md5 = ComputeMd5Task.computeMd5( inputStream );

                cacheFile( extractedFile, extractedHeader, md5, database, config, file );
            }
        }
    }

    private void cacheFile( String filename, RomHeader header, String md5, RomDatabase database, ConfigFile config, File zipFileLocation )
    {
        mListener.GetProgressDialog().setMessage( R.string.cacheRomInfo_computingMD5 );

        mListener.GetProgressDialog().setMessage( R.string.cacheRomInfo_searchingDB );
        RomDetail detail = database.lookupByMd5WithFallback( md5, filename, header.crc, header.countryCode );
        String artPath = mArtDir + "/" + detail.artName;
        config.put( md5, "goodName", detail.goodName );
        if (detail.baseName != null && detail.baseName.length() != 0)
            config.put( md5, "baseName", detail.baseName );
        config.put( md5, "romPath", filename );
        config.put( md5, "zipPath", zipFileLocation == null ? "":zipFileLocation.getAbsolutePath() );
        config.put( md5, "artPath", artPath );
        config.put( md5, "crc", header.crc );
        config.put( md5, "headerName", header.name );

        String countryCodeString = Byte.toString(header.countryCode.getValue());
        config.put( md5, "countryCode",  countryCodeString);

        mListener.GetProgressDialog().setMessage( R.string.cacheRomInfo_refreshingUI );
    }
    
    private void cacheFile( File file, RomDatabase database, ConfigFile config )
    {
        String md5 = ComputeMd5Task.computeMd5( file );
        RomHeader header = new RomHeader(file);

        cacheFile( file.getAbsolutePath(), header, md5, database, config, null );
    }
    
    private static void touchFile( String destPath )
    {
        try
        {
            OutputStream outStream = new FileOutputStream( destPath );
            try
            {
                outStream.close();
            }
            catch( IOException e )
            {
                Log.w( "CacheRomInfoService", e );
            }
        }
        catch( FileNotFoundException e )
        {
            Log.w( "CacheRomInfoService", e );
        }
    }
    
    private void downloadFile( String sourceUrl, String destPath )
    {
        File destFile = new File(destPath);
        boolean fileCreationSuccess = true;

        // Be sure destination directory exists
        FileUtil.makeDirs(destFile.getParentFile().getPath());

        // Delete the file if it already exists, we are replacing it
        if (destFile.exists())
        {
            if (destFile.delete())
            {
                Log.w( "CacheRomInfoService", "Unable to delete " + destFile.getName());
            }
        }
        
        // Download file
        InputStream inStream = null;
        OutputStream outStream = null;
        try
        {
            // Open the streams (throws exceptions)
            URL url = new URL( sourceUrl );
            inStream = url.openStream();
            outStream = new FileOutputStream( destPath );

            // Buffer the streams
            inStream = new BufferedInputStream( inStream );
            outStream = new BufferedOutputStream( outStream );
            
            // Read/write the streams (throws exceptions)
            byte[] buffer = new byte[1024];
            int n;
            while( ( n = inStream.read( buffer ) ) >= 0)
            {
                outStream.write( buffer, 0, n );
            }

            // Check if downloaded file is valud
            if (!FileUtil.isFileImage(destFile))
            {
                if (destFile.delete())
                {
                    Log.w( "CacheRomInfoService", "Deleting invalid image " + destFile.getName());
                }
            }
        }
        catch( Throwable e )
        {
            Log.w( "CacheRomInfoService", e );
            fileCreationSuccess = false;
        }
        finally
        {
            // Flush output stream and guarantee no memory leaks
            if( outStream != null )
                try
                {
                    outStream.close();
                }
                catch( IOException e )
                {
                    Log.w( "CacheRomInfoService", e );
                }
            if( inStream != null )
                try
                {
                    inStream.close();
                }
                catch( IOException e )
                {
                    Log.w( "CacheRomInfoService", e );
                }
        }
        
        if (!fileCreationSuccess && !destFile.isDirectory())
        {
            // Delete any remnants if there was an exception. We don't want a
            // corrupted graphic
            if (!destFile.delete()) {
                Log.w("CacheRomInfoService", "Unable to delete " + destFile.getName());
            }
        }
    }
    
    @Override
    public void onDestroy()
    {
        mbStopped = true;
        
        if (mListener != null)
        {
            mListener.onCacheRomInfoServiceDestroyed();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
    
    public void SetCacheRomInfoListener(CacheRomInfoListener cacheRomInfoListener)
    {
        mListener = cacheRomInfoListener;
        mListener.GetProgressDialog().setOnCancelListener(new OnCancelListener()
        {
            @Override
            public void OnCancel()
            {
                Stop();
            }
        });
        
        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = mStartId;
        mServiceHandler.sendMessage(msg);
    }

    public void Stop()
    {
        mbStopped = true;        
    }

    /**
     * Return true if the config file already contains the given zip file, this is because
     * exctracting zip files takes a long time
     * @param zipFile Zip file to search config file for
     * @return true if zip file is present
     */
    private boolean ConfigHasZip(ConfigFile theConfigFile, String zipFile)
    {
        Set<String> keys = theConfigFile.keySet();
        boolean found = false;

        Iterator iter = keys.iterator();
        String key = null;
        while (iter.hasNext() && !found) {
            key = (String) iter.next();
            String foundZipPath = theConfigFile.get(key, "zipPath");
            found = foundZipPath != null && foundZipPath.equals(zipFile);
        }

        // If found,make sure it also has  valid data
        if (found && key != null) {
            String crc = theConfigFile.get( key, "crc" );
            String headerName = theConfigFile.get( key, "headerName" );
            final String countryCodeString = theConfigFile.get( key, "countryCode" );

            found = crc != null && headerName != null && countryCodeString != null;
        }

        return found;
    }

    /**
     * Cleanup any missing files from the config file
     * @param theConfigFile Config file to clean up
     */
    private void CleanupMissingFiles(ConfigFile theConfigFile)
    {
        Set<String> keys = theConfigFile.keySet();

        Iterator iter = keys.iterator();
        while (iter.hasNext()) {
            String key = (String) iter.next();
            String foundZipPath = theConfigFile.get(key, "zipPath");
            String foundRomPath = theConfigFile.get(key, "romPath");

            //Check if this is a zip file first
            if(!TextUtils.isEmpty(foundZipPath))
            {
                File zipFile = new File(foundZipPath);

                //Zip file doesn't exist, check if the ROM path exists
                if(!zipFile.exists())
                {
                    if(!TextUtils.isEmpty(foundRomPath))
                    {
                        File romFile = new File(foundRomPath);

                        //Cleanup the ROM file since this is a zip file
                        if(!romFile.exists())
                        {
                            Log.i( "CacheRomInfoService", "Removing md5=" + key );
                            if(!romFile.isDirectory() && romFile.delete()) {
                                Log.w( "CacheRomInfoService", "Unable to delete " + romFile.getName() );
                            }
                        }
                    }

                    theConfigFile.remove(key);
                    keys = theConfigFile.keySet();
                    iter = keys.iterator();
                }
            }
            //This was not a zip file, just check the ROM path
            else if(!TextUtils.isEmpty(foundRomPath))
            {
                File romFile = new File(foundRomPath);

                //Cleanup the ROM file since this is a zip file
                if(!romFile.exists())
                {
                    Log.w( "CacheRomInfoService", "Removing md5=" + key );

                    theConfigFile.remove(key);
                    keys = theConfigFile.keySet();
                    iter = keys.iterator();
                }
            }
        }
    }

    private void downloadCoverArt(RomDatabase database, ConfigFile theConfigFile)
    {
        if( mDownloadArt )
        {
            Set<String> keys = theConfigFile.keySet();

            mListener.GetProgressDialog().setMaxProgress( keys.size() );

            mListener.GetProgressDialog().setMessage( "" );
            mListener.GetProgressDialog().setSubtext( getString(R.string.cacheRomInfo_downloadingArt) );

            for (String key : keys) {
                String artPath = theConfigFile.get(key, "artPath");
                String romFile = theConfigFile.get(key, "romPath");
                String crc = theConfigFile.get(key, "crc");
                final String countryCodeString = theConfigFile.get( key, "countryCode" );
                CountryCode countryCode = CountryCode.UNKNOWN;
                if (countryCodeString != null)
                {
                    countryCode = CountryCode.getCountryCode(Byte.parseByte(countryCodeString));
                }

                if(!TextUtils.isEmpty(artPath) && !TextUtils.isEmpty(romFile) && !TextUtils.isEmpty(crc))
                {
                    RomDetail detail = database.lookupByMd5WithFallback( key, new File(romFile).getAbsolutePath(), crc, countryCode );

                    mListener.GetProgressDialog().setText( new File(romFile).getName() );

                    //Only download art if it's not already present or current art is not a valid image
                    File artPathFile = new File (artPath);
                    if(!artPathFile.exists() || !FileUtil.isFileImage(artPathFile))
                    {
                        Log.i( "CacheRomInfoService", "Start art download: " +  artPath);
                        downloadFile( detail.artUrl, artPath );

                        Log.i( "CacheRomInfoService", "End art download: " +  artPath);
                    }
                }

                mListener.GetProgressDialog().incrementProgress(1);

                if( mbStopped ) break;
            }
        }
    }
}
