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
package paulscode.android.mupen64plusae.input;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.InputDevice.MotionRange;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

import com.bda.controller.Controller;
import com.bda.controller.ControllerListener;
import com.bda.controller.StateEvent;

import es.jlbc.retroemulatorn64.R;

import java.util.Locale;

import paulscode.android.mupen64plusae.hack.MogaHack;
import paulscode.android.mupen64plusae.input.provider.AbstractProvider;
import paulscode.android.mupen64plusae.util.DeviceUtil;
import paulscode.android.mupen64plusae.util.LocaleContextWrapper;

public class DiagnosticActivity extends AppCompatActivity implements ControllerListener
{
    private Controller mMogaController = Controller.getInstance( this );

    @Override
    protected void attachBaseContext(Context newBase) {
        if(TextUtils.isEmpty(LocaleContextWrapper.getLocalCode()))
        {
            super.attachBaseContext(newBase);
        }
        else
        {
            super.attachBaseContext(LocaleContextWrapper.wrap(newBase,LocaleContextWrapper.getLocalCode()));
        }
    }

    @Override
    public void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.diagnostic_activity );
        
        // TODO: Remove hack after MOGA SDK is fixed
        // mMogaController.init();
        MogaHack.init( mMogaController, this );
        mMogaController.setListener( this, new Handler() );
    }
    
    @Override
    public void onResume()
    {
        super.onResume();
        mMogaController.onResume();
    }
    
    @Override
    public void onPause()
    {
        super.onPause();
        mMogaController.onPause();
    }
    
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        mMogaController.exit();
    }
    
    @Override
    public void onStateEvent( StateEvent event )
    {
    }
    
    @Override
    public boolean onKeyDown( int keyCode, KeyEvent event )
    {
        onKey( event );
        return keyCode != KeyEvent.KEYCODE_BACK || super.onKeyDown( keyCode, event );
    }
    
    @Override
    public boolean onKeyUp( int keyCode, KeyEvent event )
    {
        onKey( event );

        return keyCode != KeyEvent.KEYCODE_BACK || super.onKeyUp( keyCode, event );
    }
    
    private void onKey( KeyEvent event )
    {
        int keyCode = event.getKeyCode();
        
        String message = "KeyEvent:";
        message += "\nDevice: " + getHardwareSummary( AbstractProvider.getHardwareId( event ) );
        message += "\nAction: " + DeviceUtil.getActionName( event.getAction(), false );
        message += "\nKeyCode: " + keyCode;
        message += "\n\n" + KeyEvent.keyCodeToString( keyCode );
        
        TextView view = findViewById( R.id.textKey );
        view.setText( message );
    }
    
    @Override
    public void onKeyEvent( com.bda.controller.KeyEvent event )
    {
        int keyCode = event.getKeyCode();
        
        String message = "KeyEvent:";
        message += "\nDevice: " + getHardwareSummary( AbstractProvider.getHardwareId( event ) );
        message += "\nAction: MOGA_" + DeviceUtil.getActionName( event.getAction(), false );
        message += "\nKeyCode: " + keyCode;
        message += "\n\n" + KeyEvent.keyCodeToString( keyCode );
        
        TextView view = findViewById( R.id.textKey );
        view.setText( message );
    }
    
    @Override
    public boolean onTouchEvent( MotionEvent event )
    {
        onMotion( event );
        return true;
    }
    
    @Override
    public boolean onGenericMotionEvent( MotionEvent event )
    {
        onMotion( event );
        return true;
    }
    
    private void onMotion(MotionEvent event)
    {
        StringBuilder message = new StringBuilder();

        message.append("MotionEvent:");
        message.append("\nDevice: ").append(getHardwareSummary(AbstractProvider.getHardwareId(event)));
        message.append("\nAction: ").append(DeviceUtil.getActionName(event.getAction(), true));
        message.append("\n");

        if (event.getDevice() != null) {
            for (MotionRange range : event.getDevice().getMotionRanges()) {
                int axis = range.getAxis();
                String name = MotionEvent.axisToString(axis);
                String source = DeviceUtil.getSourceName(range.getSource()).toLowerCase(Locale.US);
                float value = event.getAxisValue(axis);
                message.append(String.format(Locale.US,"\n%s (%s): %+.2f", name, source, value));
            }
        }

        TextView view = findViewById(R.id.textMotion);
        view.setText(message);
    }
    
    @Override
    public void onMotionEvent( com.bda.controller.MotionEvent event )
    {
        String message = "MotionEvent:";
        message += "\nDevice: " + getHardwareSummary( AbstractProvider.getHardwareId( event ) );
        message += "\nAction: MOGA_MOTION";
        message += "\n";
        // @formatter:off
        message += String.format(Locale.US, "\nAXIS_X (moga): %+.2f",        event.getAxisValue( com.bda.controller.MotionEvent.AXIS_X ) );
        message += String.format(Locale.US, "\nAXIS_Y (moga): %+.2f",        event.getAxisValue( com.bda.controller.MotionEvent.AXIS_Y ) );
        message += String.format(Locale.US, "\nAXIS_Z (moga): %+.2f",        event.getAxisValue( com.bda.controller.MotionEvent.AXIS_Z ) );
        message += String.format(Locale.US, "\nAXIS_RZ (moga): %+.2f",       event.getAxisValue( com.bda.controller.MotionEvent.AXIS_RZ ) );
        message += String.format(Locale.US, "\nAXIS_LTRIGGER (moga): %+.2f", event.getAxisValue( com.bda.controller.MotionEvent.AXIS_LTRIGGER ) );
        message += String.format(Locale.US, "\nAXIS_RTRIGGER (moga): %+.2f", event.getAxisValue( com.bda.controller.MotionEvent.AXIS_RTRIGGER ) );
        // @formatter:on
        TextView view = findViewById( R.id.textMotion );
        view.setText( message );
    }
    
    private static String getHardwareSummary( int hardwareId )
    {
        String name = AbstractProvider.getHardwareName( hardwareId );
        return Integer.toString( hardwareId ) + ( name == null ? "" : " (" + name + ")" );
    }
}
