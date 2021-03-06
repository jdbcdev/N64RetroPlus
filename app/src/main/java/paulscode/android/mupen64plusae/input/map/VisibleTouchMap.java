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
 * Authors: Paul Lamb, littleguy77
 */
package paulscode.android.mupen64plusae.input.map;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.util.DisplayMetrics;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

import paulscode.android.mupen64plusae.game.GameOverlay;
import paulscode.android.mupen64plusae.persistent.ConfigFile;
import paulscode.android.mupen64plusae.profile.Profile;
import paulscode.android.mupen64plusae.util.Image;
import paulscode.android.mupen64plusae.util.SafeMethods;
import paulscode.android.mupen64plusae.util.Utility;

/**
 * A kind of touch map that can be drawn on a canvas.
 * 
 * @see TouchMap
 * @see GameOverlay
 */
public class VisibleTouchMap extends TouchMap
{
    /** FPS frame image. */
    private Image mFpsFrame;
    
    /** X-coordinate of the FPS frame, in percent. */
    private int mFpsFrameX;
    
    /** Y-coordinate of the FPS frame, in percent. */
    private int mFpsFrameY;
    
    /** X-coordinate of the FPS text centroid, in percent. */
    private int mFpsTextX;
    
    /** Y-coordinate of the FPS text centroid, in percent. */
    private int mFpsTextY;
    
    /** The current FPS value. */
    private int mFpsValue;
    
    /** The minimum size of the FPS indicator in pixels. */
    private float mFpsMinPixels;
    
    /** The minimum size to scale the FPS indicator. */
    private float mFpsMinScale;
    
    /** True if the FPS indicator should be drawn. */
    private boolean mFpsEnabled;

    /* FPS indicator X position */
    private int mFpsXPos;

    /* FPS indicator Y position */
    private int mFpsYPos;
    
    /** The factor to scale images by. */
    private float mScalingFactor = 1.0f;
    
    /** Touchscreen opacity. */
    private int mTouchscreenTransparency;
    
    /** The last width passed to {@link #resize(int, int, DisplayMetrics)}. */
    private int cacheWidth = 0;
    
    /** The last height passed to {@link #resize(int, int, DisplayMetrics)}. */
    private int cacheHeight = 0;
    
    /** The last height passed to {@link #resize(int, int, DisplayMetrics)}. */
    private DisplayMetrics cacheMetrics;
    
    /** The set of images representing the FPS string. */
    private final CopyOnWriteArrayList<Image> mFpsDigits;
    
    /** The set of images representing the numerals 0, 1, 2, ..., 9. */
    private final Image[] mNumerals;
    
    /** Auto-hold overlay images. */
    private final Image[] autoHoldImages;

    /** Auto-hold overlay images pressed status */
    private final boolean[] autoHoldImagesPressed;
    
    /** X-coordinates of the AutoHold mask, in percent. */
    private final int[] autoHoldX;
    
    /** Y-coordinates of the AutoHold mask, in percent. */
    private final int[] autoHoldY;
    
    /**
     * Instantiates a new visible touch map.
     * 
     * @param resources  The resources of the activity associated with this touch map.
     */
    public VisibleTouchMap( Resources resources )
    {
        super( resources );
        mFpsDigits = new CopyOnWriteArrayList<>();
        mNumerals = new Image[10];
        autoHoldImages = new Image[NUM_N64_PSEUDOBUTTONS];
        autoHoldImagesPressed = new boolean[NUM_N64_PSEUDOBUTTONS];
        autoHoldX = new int[NUM_N64_PSEUDOBUTTONS];
        autoHoldY = new int[NUM_N64_PSEUDOBUTTONS];
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see paulscode.android.mupen64plusae.input.map.TouchMap#clear()
     */
    @Override
    public void clear()
    {
        super.clear();
        mFpsFrame = null;
        mFpsFrameX = mFpsFrameY = 0;
        mFpsTextX = mFpsTextY = 50;
        mFpsValue = 0;
        mFpsDigits.clear();
        for( int i = 0; i < mNumerals.length; i++ )
            mNumerals[i] = null;
        for( int i = 0; i < autoHoldImages.length; i++ )
        {
            autoHoldImagesPressed[i] = false;
            autoHoldImages[i] = null;
        }
        for( int i = 0; i < autoHoldX.length; i++ )
            autoHoldX[i] = 0;
        for( int i = 0; i < autoHoldY.length; i++ )
            autoHoldY[i] = 0;
    }
    
    /**
     * Recomputes the map data for a given digitizer size, and
     * recalculates the scaling factor.
     * 
     * @param w The width of the digitizer, in pixels.
     * @param h The height of the digitizer, in pixels.
     * @param metrics Metrics about the display (for use in scaling).
     */
    public void resize( int w, int h, DisplayMetrics metrics )
    {
        // Cache the width and height in case we need to reload assets
        cacheWidth = w;
        cacheHeight = h;
        cacheMetrics = metrics;
        scale = 1.0f;
        
        if( metrics != null )
        {
            scale = metrics.densityDpi/260.0f;
        }
        // Apply the global scaling factor (derived from user prefs)
        scale *= mScalingFactor;
        
        resize( w, h );
    }
    
    /**
     * Returns true if A/B buttons are split
     * 
     */
    public boolean isABSplit()
    {
        return mSplitAB;
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see paulscode.android.mupen64plusae.input.map.TouchMap#resize(int, int)
     */
    @Override
    public void resize( int w, int h )
    {
        super.resize( w, h );
        
        // Compute analog foreground location (centered)
        if( analogBackImage != null && analogForeImage != null )
        {
            int cX = analogBackImage.x + (int) ( analogBackImage.hWidth * ( analogBackScaling * scale ) );
            int cY = analogBackImage.y + (int) ( analogBackImage.hHeight * ( analogBackScaling * scale ) );
            analogForeImage.setScale( ( analogBackScaling * scale ) );
            analogForeImage.fitCenter( cX, cY, analogBackImage.x, analogBackImage.y,
                    (int) ( analogBackImage.width * ( analogBackScaling * scale ) ), (int) ( analogBackImage.height * ( analogBackScaling * scale ) ) );
        }
        
        // Compute auto-hold overlay locations
        for( int i = 0; i < autoHoldImages.length; i++ )
        {
            if( autoHoldImages[i] != null )
            {
                String name = ASSET_NAMES.get( i );
                float scaling = 1.f;
                
                for( int j = 0; j < buttonNames.size(); j++ )
                {
                    if ( buttonNames.get( j ).equals( name ) )
                        scaling = buttonScaling.get( j );
                }
                
                autoHoldImages[i].setScale( ( scaling * scale ) );
                autoHoldImages[i].fitPercent( autoHoldX[i], getAdjustedYPos(autoHoldY[i]), w, h );
            }
        }
        
        // Compute FPS frame location
        float fpsScale = scale;
        if( mFpsMinScale > scale )
            fpsScale = mFpsMinScale;
        if( mFpsFrame != null )
        {
            mFpsFrame.setScale( fpsScale );
            mFpsFrame.fitPercent( mFpsFrameX, getAdjustedFpsYPos(mFpsFrameY), w, h );
        }
        for( Image image : mNumerals)
        {
            if( image != null )
                image.setScale( fpsScale );
        }
        
        // Compute the FPS digit locations
        refreshFpsImages();
        refreshFpsPositions();
    }
    
    /**
     * Draws the buttons.
     * 
     * @param canvas The canvas on which to draw.
     */
    public void drawButtons( Canvas canvas )
    {
        // Draw the buttons onto the canvas
        for( Image button : buttonImages )
        {
            button.draw( canvas );
        }
    }
    
    /**
     * Draws the AutoHold mask.
     * 
     * @param canvas The canvas on which to draw.
     */
    public void drawAutoHold( Canvas canvas )
    {
        // Draw the AutoHold mask onto the canvas
        for( Image autoHoldImage : autoHoldImages )
        {
            if( autoHoldImage != null )
            {
                autoHoldImage.draw( canvas );
            }
        }
    }
    
    /**
     * Draws the analog stick.
     * 
     * @param canvas The canvas on which to draw.
     */
    public void drawAnalog( Canvas canvas )
    {
        // Draw the background image
        if( analogBackImage != null )
        {
            analogBackImage.draw( canvas );
        }
        
        // Draw the movable foreground (the stick)
        if( analogForeImage != null )
        {
            analogForeImage.draw( canvas );
        }
    }
    
    /**
     * Draws the FPS indicator.
     * 
     * @param canvas The canvas on which to draw.
     */
    public void drawFps( Canvas canvas )
    {
        if( canvas == null )
            return;
        
        // Redraw the FPS indicator
        if( mFpsFrame != null )
            mFpsFrame.draw( canvas );
        
        // Draw each digit of the FPS number
        for( Image digit : mFpsDigits )
            digit.draw( canvas );
    }
    
    /**
     * Updates the analog stick assets to reflect a new position.
     * 
     * @param axisFractionX The x-axis fraction, between -1 and 1, inclusive.
     * @param axisFractionY The y-axis fraction, between -1 and 1, inclusive.
     * 
     * @return True if the analog assets changed.
     */
    public boolean updateAnalog( float axisFractionX, float axisFractionY )
    {
        if( analogForeImage != null && analogBackImage != null )
        {
            // Get the location of stick center
            int hX = (int) ( ( analogBackImage.hWidth + ( axisFractionX * analogMaximum ) ) * ( analogBackScaling * scale ) );
            int hY = (int) ( ( analogBackImage.hHeight - ( axisFractionY * analogMaximum ) ) * ( analogBackScaling * scale ) );
            
            // Use other values if invalid
            if( hX < 0 )
                hX = (int) ( analogBackImage.hWidth * ( analogBackScaling * scale ) );
            if( hY < 0 )
                hY = (int) ( analogBackImage.hHeight * ( analogBackScaling * scale ) );

            int width = (int) ( analogBackImage.width * ( analogBackScaling * scale ) );
            int height = (int) ( analogBackImage.height * ( analogBackScaling * scale ) );

            // Update position of the surrounding graphic
            analogBackImage.fitCenter(currentAnalogX + hX, currentAnalogY + hY, currentAnalogX, currentAnalogY, width, height);

            // Update the position of the stick
            analogForeImage.fitCenter(currentAnalogX + hX, currentAnalogY + hY, currentAnalogX, currentAnalogY, width, height );
            return true;
        }
        return false;
    }
    
    /**
     * Updates the FPS indicator assets to reflect a new value.
     * 
     * @param fps The new FPS value.
     * 
     * @return True if the FPS assets changed.
     */
    public boolean updateFps( int fps )
    {
        // Clamp to positive, four digits max [0 - 9999]
        fps = Utility.clamp( fps, 0, 9999 );
        
        // Quick return if user has disabled FPS or it hasn't changed
        if( !mFpsEnabled || mFpsValue == fps )
            return false;
        
        // Store the new value
        mFpsValue = fps;
        
        // Refresh the FPS digits
        refreshFpsImages();
        refreshFpsPositions();
        
        return true;
    }
    
    /**
     * Updates the auto-hold assets to reflect a new value.
     * 
     * @param pressed The new autohold state value.
     * @param index   The index of the auto-hold mask.
     * 
     * @return True if the autohold assets changed.
     */
    public boolean updateAutoHold( boolean pressed, int index )
    {
        autoHoldImagesPressed[index] = pressed;

        if( autoHoldImages[index] != null )
        {
            if( pressed )
                autoHoldImages[index].setAlpha( mTouchscreenTransparency );
            else
                autoHoldImages[index].setAlpha( 0 );
            return true;
        }
        return false;
    }
    
    /**
     * Refreshes the images used to draw the FPS string.
     */
    private void refreshFpsImages()
    {
        // Refresh the list of FPS digits
        String fpsString = Integer.toString( mFpsValue );
        mFpsDigits.clear();
        for( int i = 0; i < 4; i++ )
        {
            // Create a new sequence of numeral images
            if( i < fpsString.length() )
            {
                int numeral = SafeMethods.toInt( fpsString.substring( i, i + 1 ), -1 );
                if( numeral > -1 && numeral < 10 )
                {
                    // Clone the numeral from the font images and move to next digit
                    mFpsDigits.add( new Image( mResources, mNumerals[numeral] ) );
                }
            }
        }
    }
    
    /**
     * Refreshes the positions of the FPS images.
     */
    private void refreshFpsPositions()
    {
        // Compute the centroid of the FPS text
        int x = 0;
        int y = 0;
        if( mFpsFrame != null )
        {
            x = mFpsFrame.x + (int) ( ( mFpsFrame.width * mFpsFrame.scale ) * ( mFpsTextX / 100f ) );
            y = mFpsFrame.y + (int) ( ( mFpsFrame.height * mFpsFrame.scale ) * ( mFpsTextY / 100f ) );
        }
        
        // Compute the width of the FPS text
        int totalWidth = 0;
        for( Image digit : mFpsDigits )
            totalWidth += (int) ( digit.width * digit.scale );
        
        // Compute the starting position of the FPS text
        x -= (int) ( totalWidth / 2f );
        
        // Compute the position of each digit
        for( Image digit : mFpsDigits )
        {
            digit.setPos( x, y - (int) ( digit.hHeight * digit.scale ) );
            x += (int) ( digit.width * digit.scale );
        }
    }
    
    /**
     * Loads all touch map data from the filesystem.
     * 
     * @param skinDir    The directory containing the skin.ini and image files.
     * @param profile    The name of the touchscreen profile.
     * @param animated   True to load the analog assets in two parts for animation.
     * @param fpsEnabled True to display the FPS indicator.
     * @param scale      The factor to scale images by.
     * @param alpha      The opacity of the visible elements.
     */
    public void load( String skinDir, Profile profile, boolean animated, boolean fpsEnabled, int fpsXPos, int fpsYPos,
                      float scale, int alpha )
    {
        mFpsEnabled = fpsEnabled;
        mFpsXPos = fpsXPos;
        mFpsYPos = fpsYPos;
        mScalingFactor = scale;
        mTouchscreenTransparency = alpha;
        
        super.load( skinDir, profile, animated );
        ConfigFile skin_ini = new ConfigFile( skinFolder + "/skin.ini" );
        mFpsTextX = SafeMethods.toInt( skin_ini.get( "INFO", "fps-numx" ), 27 );
        mFpsTextY = SafeMethods.toInt( skin_ini.get( "INFO", "fps-numy" ), 50 );
        mFpsMinPixels = SafeMethods.toInt( skin_ini.get( "INFO", "fps-minPixels" ), 75 );

        // Scale the assets to the last screensize used
        resize( cacheWidth, cacheHeight, cacheMetrics );
    }
    
    /**
     * Refreshes the position of a touchscreen button image.
     *
     * @param profile    The name of the touchscreen profile.
     * @param name       The name of the button.
     */
    public void refreshButtonPosition( Profile profile, String name )
    {
        super.updateButton( profile, name, cacheWidth, cacheHeight );
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see
     * paulscode.android.mupen64plusae.input.map.TouchMap#loadAllAssets(paulscode.android.mupen64plusae
     * .profile.Profile, boolean)
     */
    @Override
    protected void loadAllAssets( Profile profile, boolean animated )
    {
        super.loadAllAssets( profile, animated );
        
        // Set the transparency of the images
        for( Image buttonImage : buttonImages )
        {
            buttonImage.setAlpha( mTouchscreenTransparency );
        }
        if( analogBackImage != null )
        {
            analogBackImage.setAlpha( mTouchscreenTransparency );
        }
        if( analogForeImage != null )
        {
            analogForeImage.setAlpha( mTouchscreenTransparency );
        }
        
        // Load the FPS and autohold images
        if( profile != null )
        {
            loadFpsIndicator();
            if( mSplitAB  )
            {
                loadAutoHoldImages( profile, "buttonA-holdA" );
                loadAutoHoldImages( profile, "buttonB-holdB" );
            }
            else
            {
                loadAutoHoldImages( profile, "groupAB-holdA" );
                loadAutoHoldImages( profile, "groupAB-holdB" );
            }
            loadAutoHoldImages( profile, "groupC-holdCu" );
            loadAutoHoldImages( profile, "groupC-holdCd" );
            loadAutoHoldImages( profile, "groupC-holdCl" );
            loadAutoHoldImages( profile, "groupC-holdCr" );
            loadAutoHoldImages( profile, "buttonL-holdL" );
            loadAutoHoldImages( profile, "buttonR-holdR" );
            loadAutoHoldImages( profile, "buttonZ-holdZ" );
            loadAutoHoldImages( profile, "buttonS-holdS" );
            loadAutoHoldImages( profile, "buttonSen-holdSen" );
        }
    }
    
    @Override
    public void setAnalogEnabled(boolean enabled) {
        super.setAnalogEnabled(enabled);
        if (analogBackImage != null) {
            if (enabled) {
                analogBackImage.setAlpha(mTouchscreenTransparency);
            } else {
                analogBackImage.setAlpha(0);
            }
        }
        if (analogForeImage != null) {
            if (enabled) {
                analogForeImage.setAlpha(mTouchscreenTransparency);
            } else {
                analogForeImage.setAlpha(0);
            }
        }
    }



    /**
     * Hides the touch controller
     */
    public boolean hideTouchController()
    {
        // Set the transparency of the images
        for( Image buttonImage : buttonImages )
        {
            if(buttonImage != null)
                buttonImage.setAlpha( 0 );
        }

        for( Image autoHoldImage : autoHoldImages)
        {
            if(autoHoldImage != null )
                autoHoldImage.setAlpha( 0 );
        }

        if( analogBackImage != null )
        {
            analogBackImage.setAlpha( 0 );
        }
        if( analogForeImage != null )
        {
            analogForeImage.setAlpha( 0 );
        }

        return true;
    }

    /**
     * Shows the touch controller
     */
    public void setTouchControllerAlpha(double alpha)
    {
        if (alpha < 0) {
            alpha = 0;
        } else if (alpha > 1.0) {
            alpha = 1.0;
        }
        // Set the transparency of the images
        for( Image buttonImage : buttonImages )
        {
            if(buttonImage != null)
                buttonImage.setAlpha( (int)(mTouchscreenTransparency*alpha) );
        }

        for( int index = 0; index < autoHoldImages.length; ++index)
        {

            Image autoHoldImage = autoHoldImages[index];
            if(autoHoldImage != null && autoHoldImagesPressed[index] )
                autoHoldImage.setAlpha( (int)(mTouchscreenTransparency*alpha) );
        }

        if (analogBackImage != null) {
            analogBackImage.setAlpha((int)(mTouchscreenTransparency*alpha));
        }
        if (analogForeImage != null) {
            analogForeImage.setAlpha((int)(mTouchscreenTransparency*alpha));
        }
    }

    /**
     * Shows the touch controller
     */
    public boolean showTouchController()
    {
        // Set the transparency of the images
        for( Image buttonImage : buttonImages )
        {
            if(buttonImage != null)
                buttonImage.setAlpha( mTouchscreenTransparency );
        }

        for( int index = 0; index < autoHoldImages.length; ++index)
        {

            Image autoHoldImage = autoHoldImages[index];
            if(autoHoldImage != null && autoHoldImagesPressed[index] )
                autoHoldImage.setAlpha( mTouchscreenTransparency );
        }

        if (analogBackImage != null) {
            analogBackImage.setAlpha(mTouchscreenTransparency);
        }
        if (analogForeImage != null) {
            analogForeImage.setAlpha(mTouchscreenTransparency);
        }
        return true;
    }

    /**
     * Loads FPS indicator assets and properties from the filesystem.
     */
    private void loadFpsIndicator()
    {
        if( mFpsXPos >= 0 && mFpsYPos >= 0 )
        {
            // Position (percentages of the screen dimensions)
            mFpsFrameX = mFpsXPos;
            mFpsFrameY = mFpsYPos;
            
            // Load frame image
            mFpsFrame = new Image( mResources, skinFolder + "/fps.png" );
            
            // Minimum factor the FPS indicator can be scaled by
            mFpsMinScale = mFpsMinPixels / (float) mFpsFrame.width;
            
            // Load numeral images
            String filename = "";
            try
            {
                // Make sure we can load them (they might not even exist)
                for( int i = 0; i < mNumerals.length; i++ )
                {
                    filename = skinFolder + "/fps-" + i + ".png";
                    mNumerals[i] = new Image( mResources, filename );
                }
            }
            catch( Exception e )
            {
                // Problem, let the user know
                Log.e( "VisibleTouchMap", "Problem loading fps numeral '" + filename
                        + "', error message: " + e.getMessage() );
            }
        }
    }
    
    /**
     * Loads auto-hold assets and properties from the filesystem.
     * 
     * @param profile The touchscreen profile containing the auto-hold properties.
     * @param name The name of the image to load.
     */
    private void loadAutoHoldImages( Profile profile, String name )
    {
        if ( !name.contains("-hold") )
            return;
        
        String[] fields = name.split( "-hold" );
        String group = fields[0];
        String hold = fields[1];
        
        int x = profile.getInt( group + "-x", -1 );
        int y = profile.getInt( group + "-y", -1 );
        Integer index = MASK_KEYS.get( hold );
        
        if( x >= 0 && y >= 0 && index != null )
        {
            // Position (percentages of the digitizer dimensions)
            autoHoldX[index] = x;
            autoHoldY[index] = y;
            
            // The drawable image is in PNG image format.
            autoHoldImages[index] = new Image( mResources, skinFolder + "/" + name + ".png" );
            autoHoldImages[index].setAlpha( 0 );
        }
    }
}
