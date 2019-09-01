package es.jdbc.n64retroplus;

import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import es.jdbc.n64retroplus.dialog.ConfirmationDialog;
import es.jdbc.n64retroplus.dialog.ConfirmationDialog.PromptConfirmListener;
import es.jdbc.n64retroplus.dialog.Prompt;
import es.jdbc.n64retroplus.persistent.AppData;
import es.jdbc.n64retroplus.persistent.GlobalPrefs;
import es.jdbc.n64retroplus.util.FileUtil;
import es.jdbc.n64retroplus.DeleteFilesFragment.DeleteFilesFinishedListener;

public class DeleteTexturesActivity extends AppCompatActivity implements OnItemClickListener, PromptConfirmListener,
        DeleteFilesFinishedListener
{    
    private List<String> mPaths;
    private GlobalPrefs mGlobalPrefs = null;
    private DeleteFilesFragment mDeleteFilesFragment = null;
    
    private String mCurrentPath = null;
    public static final int CLEAR_CONFIRM_DIALOG_ID = 0;
    private static final String STATE_CLEAR_CONFIRM_DIALOG = "STATE_CLEAR_CONFIRM_DIALOG";
    private static final String STATE_DELETE_FILES_FRAGMENT= "STATE_DELETE_FILES_FRAGMENT";
 
    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        super.onCreate(savedInstanceState);
        
        String currentPath = null;
        
        if(savedInstanceState != null)
        {
            currentPath = savedInstanceState.getString( ActivityHelper.Keys.SEARCH_PATH );
        }

        if( currentPath != null )
        {
            mCurrentPath = currentPath;
        }

        final FragmentManager fm = getSupportFragmentManager();
        mDeleteFilesFragment = (DeleteFilesFragment) fm.findFragmentByTag(STATE_DELETE_FILES_FRAGMENT);

        if(mDeleteFilesFragment == null)
        {
            mDeleteFilesFragment = new DeleteFilesFragment();
            fm.beginTransaction().add(mDeleteFilesFragment, STATE_DELETE_FILES_FRAGMENT).commit();
        }
         
        setContentView(R.layout.delete_textures_activity);

        AppData appData = new AppData( this );
        mGlobalPrefs = new GlobalPrefs( this, appData );
        
        Button cancelButton = findViewById( R.id.buttonCancel );
        cancelButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                DeleteTexturesActivity.this.setResult(RESULT_CANCELED, null);
                DeleteTexturesActivity.this.finish();
            }
        });
        
        Button deleteButton = findViewById( R.id.buttonDelete );
        deleteButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                String title = getString( R.string.confirm_title );
                String message = getString( R.string.confirmClearData_message );

                ConfirmationDialog confirmationDialog =
                        ConfirmationDialog.newInstance(CLEAR_CONFIRM_DIALOG_ID, title, message);

                FragmentManager fm = getSupportFragmentManager();
                confirmationDialog.show(fm, STATE_CLEAR_CONFIRM_DIALOG);
            }
        });

        PopulateFileList();
    }
    
    @Override
    public void onSaveInstanceState( Bundle savedInstanceState )
    {
        if( mCurrentPath != null )
            savedInstanceState.putString( ActivityHelper.Keys.SEARCH_PATH, mCurrentPath );

        super.onSaveInstanceState( savedInstanceState );
    }

    @Override
    public void onPromptDialogClosed(int id, int which)
    {
        if(id == CLEAR_CONFIRM_DIALOG_ID)
        {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                ArrayList<String> foldersToDelete = new ArrayList<>();
                foldersToDelete.add(mCurrentPath);

                ArrayList<String> filters = new ArrayList<>();
                filters.add("");

                mDeleteFilesFragment.deleteFiles(foldersToDelete, filters);
            }
        }
    }
    
    private void PopulateFileList()
    {
        if (!TextUtils.isEmpty(mCurrentPath)) {
            setTitle( new File(mCurrentPath).getName() );
        } else {
            setTitle("");
        }

        // Populate the file list
        // Get the filenames and absolute paths
        List<CharSequence> names = new ArrayList<>();
        mPaths = new ArrayList<>();

        FileUtil.populate( new File(mGlobalPrefs.hiResTextureDir), false, true, true, names, mPaths );
        FileUtil.populate( new File(mGlobalPrefs.textureCacheDir), false, true, true, names, mPaths );

        ListView listView1 = findViewById( R.id.listView1 );
        ArrayAdapter<String> adapter = Prompt.createFilenameAdapter( this, mPaths, names );
        listView1.setAdapter( adapter );
        listView1.setOnItemClickListener( this );
    }

    @Override
    public void onItemClick( AdapterView<?> parent, View view, int position, long id )
    {
        if (position < mPaths.size()) {
            mCurrentPath = mPaths.get( position );
            setTitle( new File(mCurrentPath).getName() );
        }
    }

    @Override
    public void onDeleteFilesFinished()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCurrentPath = "";
                PopulateFileList();
            }
        });
    }
}
