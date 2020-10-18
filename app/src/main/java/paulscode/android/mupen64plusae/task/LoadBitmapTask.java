/*
 * Mupen64PlusAE, an N64 emulator for the Android platform
 * 
 * Copyright (C) 2015 Paul Lamb
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

import java.io.File;
import java.lang.ref.WeakReference;

import es.jlbc.retroemulatorn64.R;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.widget.ImageView;

import paulscode.android.mupen64plusae.util.FileUtil;

public class LoadBitmapTask extends AsyncTask<String, String, String>
{
    
    private final String mBitmapPath;
    private final WeakReference<ImageView> mArtView;
    private BitmapDrawable mArtBitmap;
    private final WeakReference<Context> mContext;
    private boolean mIsCancelled;
    
    public LoadBitmapTask( Context context, String bitmapPath, ImageView artView)
    {
        mBitmapPath = bitmapPath;
        mArtView = new WeakReference<>(artView);
        mArtBitmap = null;
        mContext = new WeakReference<>(context);
        mIsCancelled = false;
    }

    @Override
    protected String doInBackground(String... params)
    {
        Context tempContext = mContext.get();

        if( !TextUtils.isEmpty( mBitmapPath ) && new File( mBitmapPath ).exists() && tempContext != null )
        {
            // Check if valid image
            if (FileUtil.isFileImage(new File(mBitmapPath))) {
                mArtBitmap = new BitmapDrawable( tempContext.getResources(), mBitmapPath );
            }
        }
        return null;
    }
    
    @Override
    protected void onPostExecute( String result )
    {
        ImageView tempArtView = mArtView.get();

        if(!mIsCancelled && tempArtView != null)
        {
            if( mArtBitmap != null )
                tempArtView.setImageDrawable( mArtBitmap );
            else
                tempArtView.setImageResource( R.drawable.default_coverart );

            tempArtView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
    }
    
    @Override
    protected void onCancelled() {
        super.onCancelled();
        
        mIsCancelled = true;
    }

}