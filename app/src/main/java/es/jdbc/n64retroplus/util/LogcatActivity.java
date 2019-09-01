package es.jdbc.n64retroplus.util;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import es.jdbc.n64retroplus.R;

import es.jdbc.n64retroplus.ActivityHelper;

public class LogcatActivity extends AppCompatActivity
{
    private Button mCancelButton;
    private Button mShareButton;
    private TextView mLogText;
    private ScrollView mTextScroll;
    private String mLogTextString = null;

    private final String TEXT_KEY = "TEXT_KEY";
    private final String TEXT_SCROLL = "TEXT_SCROLL";

    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.logcat_activity);

        mTextScroll = findViewById( R.id.logcatScroll );

        if(savedInstanceState != null)
        {
            mLogTextString = savedInstanceState.getString(TEXT_KEY);

            final int[] position = savedInstanceState.getIntArray(TEXT_SCROLL);
            if(position != null)
                mTextScroll.post(new Runnable() {
                    public void run() {
                        mTextScroll.scrollTo(position[0], position[1]);
                    }
                });
        }

        if(mLogTextString == null)
        {
            mLogTextString = DeviceUtil.getLogCat();
            mLogTextString = mLogTextString.replace("]\n", "]: ");
            mLogTextString = mLogTextString.replace("\r\n", "\n");
        }

        mLogText = findViewById( R.id.logcatText );
        mLogText.setText(mLogTextString);

        mCancelButton = findViewById( R.id.logcatCancel );
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                LogcatActivity.this.finish();
            }
        });

        mShareButton = findViewById( R.id.logcatShare );
        mShareButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ActivityHelper.launchPlainText( getBaseContext(), mLogTextString,
                        getText( R.string.actionShare_title ));
            }
        });
    }
    
    @Override
    public void onSaveInstanceState( Bundle savedInstanceState )
    {
        if( mLogTextString != null )
            savedInstanceState.putString(TEXT_KEY, mLogTextString);

        if(mTextScroll != null)
            savedInstanceState.putIntArray(TEXT_SCROLL, new int[]{ mTextScroll.getScrollX(), mTextScroll.getScrollY()});

        super.onSaveInstanceState( savedInstanceState );
    }
}
