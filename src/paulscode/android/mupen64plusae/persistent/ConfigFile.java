/**
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
 * Authors: Paul Lamb, lioncash
 */
package paulscode.android.mupen64plusae.persistent;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;

import android.text.TextUtils;
import android.util.Log;

/**
 * The ConfigFile class is used to load the parameters from a config file.
 * </p>
 * The file must follow a specific syntax:
 * <ul>
 * <li>Parameters are assigned to values with the equal sign (ex: param=value  ).
 * <li>Parameters are allowed to have empty assignments (ex: param=  ).
 * <li>Each parameter=value pair must be on a single line.
 * <li>Values may be enclosed in double-quotes (optional)  (ex:  param="value"  ).
 * <li>All parameters must be after a section title.
 * <li>Section titles are enclosed in brackets (ex. "[SECTION]")
 * <li>Comments consist of single lines, and begin with # or ; or // (ex:  ;comment  ).
 * <li>Leading and trailing whitespace in lines, param names, and values is discarded.
 * <li>Whitespace inside brackets or double-quotes is not discarded.
 * </ul>
 * 
 * @author Paul Lamb
 */
public class ConfigFile
{
    /** The name we use for the untitled section (preamble) of the config file. */
    public static final String SECTIONLESS_NAME = "[<sectionless!>]";
    
    /** Name of the config file. */
    private final String mFilename;
    
    /** Sections mapped by title for easy lookup. */
    private final HashMap<String, ConfigSection> mConfigMap;
    
    /** Sections in proper order for easy saving. */
    private final LinkedList<ConfigSection> mConfigList;

    /**
     * Constructor: Reads the entire config file, and saves the data in 'configMap'.
     * 
     * @param filename The config file to read from.
     */
    public ConfigFile( String filename )
    {
        mFilename = filename;
        mConfigMap = new HashMap<String, ConfigSection>();
        mConfigList = new LinkedList<ConfigSection>();
        reload();
    }

    /**
     * Looks up a config section that matches the specified regex (not necessarily the only match).
     * 
     * @param regex A regular expression to match a section title from.
     * 
     * @return ConfigSection containing parameters for a config section that matches, or null if no matches were found.
     */
    public ConfigSection match( String regex )
    {
        String sectionTitle;
        Set<String> keys = mConfigMap.keySet();

        for (String key : keys)
        {
            sectionTitle = key;
            if (sectionTitle.matches(regex))
                return mConfigMap.get(sectionTitle);
        }
        
        return null;
    }

    /**
     * Looks up a config section by its title.
     * 
     * @param sectionTitle Title of the section containing the parameter.
     * 
     * @return A ConfigSection containing parameters, or null if not found.
     */
    public ConfigSection get( String sectionTitle )
    {
        return mConfigMap.get( sectionTitle );
    }

    /**
     * Removes a config section by its title.  Note that the removal is not actually persisted to
     * disk until the {@link #save()} method is called.
     * 
     * @param sectionTitle Title of the section containing the parameter.
     */
    public void remove( String sectionTitle )
    {
        mConfigList.remove( mConfigMap.get( sectionTitle ) );
        mConfigMap.remove( sectionTitle );
    }

    /**
     * Looks up the specified parameter under the specified section title.
     * 
     * @param sectionTitle Title of the section containing the parameter.
     * @param parameter    Name of the parameter.
     * 
     * @return The value of the specified parameter, or null if not found.
     */
    public String get( String sectionTitle, String parameter )
    {
        ConfigSection section = mConfigMap.get( sectionTitle );
        
        // The specified section doesn't exist or is empty.. quit
        if( section == null || section.parameters == null )
            return null;
        
        ConfigParameter confParam = section.parameters.get( parameter );
        
        // The specified parameter doesn't exist.. quit
        if( confParam == null )
            return null;
        
        // Got it
        return confParam.value;
    }

    /**
     * Assigns the specified value to the specified parameter under the
     * specified section.
     * 
     * @param sectionTitle The title of the section to contain the parameter.
     * @param parameter    The name of the parameter.
     * @param value        The value to give the parameter.
     */
    public void put( String sectionTitle, String parameter, String value )
    {
        ConfigSection section = mConfigMap.get( sectionTitle );
        if( section == null )
        {  
            // Add a new section
            section = new ConfigSection( sectionTitle );
            mConfigMap.put( sectionTitle, section );
            mConfigList.add( section );
        }
        section.put( parameter, value );
    }

    /**
     * Erases any previously loaded data.
     */
    public void clear()
    {
        mConfigMap.clear();
        mConfigList.clear();  // Ready to start fresh
    }

    /**
     * Re-loads the entire config file, overwriting any unsaved changes, and saves the data in
     * 'configMap'.
     * 
     * @return True if successful.
     * @see #save()
     */
    public boolean reload()
    {   
        // Make sure a file was actually specified
        if( TextUtils.isEmpty( mFilename ) )
            return false;
        
        // Free any previously loaded data
        clear();
        
        FileInputStream fstream;
        try
        {
            fstream = new FileInputStream( mFilename );
        }
        catch( FileNotFoundException fnfe )
        {
            // File not found... we can't continue
            return false;
        }
        
        DataInputStream in = new DataInputStream( fstream );
        BufferedReader br = new BufferedReader( new InputStreamReader( in ) );

        String sectionName = SECTIONLESS_NAME;
        ConfigSection section = new ConfigSection( sectionName, br ); // Read the 'sectionless' section
        mConfigMap.put( sectionName, section ); // Save the data to 'configMap'
        mConfigList.add( section ); // Add it to the list as well
        
        // Loop through reading the remaining sections
        while( !TextUtils.isEmpty( section.nextName ) )
        {
            // Get the next section name
            sectionName = section.nextName;
 
            // Load the next section
            section = new ConfigSection( sectionName, br );
            mConfigMap.put( sectionName, section );  // Save the data to 'configMap'
            mConfigList.add( section );  // Add it to the list as well  
        }
        
        try
        {
            // Finished. Close the file.
            in.close();
            br.close();
        }
        catch( IOException ioe )
        {
            // (Don't care)
        }
        
        // Success
        return true;
    }
    
    /**
     * Saves the data from 'configMap' back to the config file.
     * 
     * @return True if successful. False otherwise.
     * @see #reload()
     */
    public boolean save()
    {
        // No filename was specified.
        if( TextUtils.isEmpty( mFilename ) )
        {
            Log.e( "ConfigFile", "Filename not specified in method save()" );
            return false;   // Quit
        }
        
        FileWriter fw = null;
        try
        {
            fw = new FileWriter( mFilename );

            // Loop through the sections
            for ( ConfigSection section : mConfigList )
            {
                if( section != null )
                    section.save( fw );
            }
        }
        catch( IOException ioe )
        {
            Log.e( "ConfigFile", "IOException creating file " + mFilename + ", error message: " + ioe.getMessage() );
            return false;  // Some problem creating the file.. quit
        }
        finally
        {
            if( fw != null )
            {
                try
                {
                    fw.close();
                }
                catch( IOException ignored )
                {
                }
            }
        }
        
        // Success
        return true;
    }
    
    /**
     * Returns a handle to the configMap keyset.
     * 
     * @return keyset containing all the config section titles.
     */
    public Set<String> keySet()
    {
         return mConfigMap.keySet();
    }
    
    /**
     * The ConfigSection class reads all the parameters in the next section of the config file.
     * Saves the name of the next section (or null if end of file or error).
     * Can also be used to add a new section to an existing configuration.
     */
    public static class ConfigSection
    {
        public String name;  // Section name
        private HashMap<String, ConfigParameter> parameters;  // Parameters sorted by name for easy lookup
        private LinkedList<ConfigLine> lines;  // All the lines in this section, including comments
        
        // Name of the next section, or null if there are no sections left to read in the file:
        private String nextName = null;
    
        /**
         * Constructor: Creates an empty config section
         * 
         * @param sectionName The section title.
         */
        public ConfigSection( String sectionName )
        {
            parameters = new HashMap<String, ConfigParameter>();
            lines = new LinkedList<ConfigLine>();
            
            if( !TextUtils.isEmpty( sectionName ) && !sectionName.equals( SECTIONLESS_NAME ) )
                lines.add( new ConfigLine( ConfigLine.LINE_SECTION, "[" + sectionName + "]\n", null ) );
            
            name = sectionName;
        }
    
        // TODO: Clean this method up a bit?
        /**
         * Constructor: Reads the next section of the config file, and saves it in 'parameters'.
         * 
         * @param sectionName The section title.
         * @param br          The config file to read from.
         */
        public ConfigSection( String sectionName, BufferedReader br )
        {
            String fullLine, strLine, p, v;
            ConfigParameter confParam;
            int x, y;
            
            parameters = new HashMap<String, ConfigParameter>();
            lines = new LinkedList<ConfigLine>();
    
            if( !TextUtils.isEmpty( sectionName ) && !sectionName.equals( SECTIONLESS_NAME ) )
                lines.add( new ConfigLine( ConfigLine.LINE_SECTION, "[" + sectionName + "]\n", null ) );
    
            name = sectionName;
            
            // No file to read from. Quit.
            if( br == null )
                return;
    
            try
            {
                while( ( fullLine = br.readLine() ) != null )
                {
                    strLine = fullLine.trim();
                    if( (strLine.length() < 1) ||
                        (strLine.substring( 0, 1 ).equals( "#" )) ||
                        (strLine.substring( 0, 1 ).equals( ";" )) ||
                        ( (strLine.length() > 1) && (strLine.substring( 0, 2 ).equals( "//" )) ) )  // NOTE: isEmpty() not supported on some devices
                    {  // A comment or blank line.
                        lines.add( new ConfigLine( ConfigLine.LINE_GARBAGE, fullLine + "\n", null ) );
                    }
                    else if( strLine.contains( "=" ) )
                    {   
                        // This should be a "parameter=value" pair:
                        x = strLine.indexOf( '=' );
                        
                        if( x < 1 )
                            return;  // This shouldn't happen (bad syntax). Quit.
                        
                        if( x < (strLine.length() - 1) )
                        {
                            p = strLine.substring( 0, x ).trim();
                            if( p.length() < 1 )
                                return;  // This shouldn't happen (bad syntax). Quit.
                            
                            v = strLine.substring( x + 1, strLine.length() ).trim();
                            //v = v.replace( "\"", "" );  // I'm doing this later, so I can save back without losing them
                            
                            if( v.length() > 0 )
                            {  
                                // Save the parameter=value pair
                                confParam = parameters.get( p );
                                if( confParam != null )
                                {
                                    confParam.value = v;
                                }
                                else
                                {
                                    confParam = new ConfigParameter( p, v );
                                    lines.add( new ConfigLine( ConfigLine.LINE_PARAM, fullLine + "\n", confParam ) );
                                    parameters.put( p, confParam );  // Save the pair.
                                }
                            }
                        }  // It's ok to have an empty assignment (such as "param=")
                    }
                    else if( strLine.contains( "[" ) )
                    {   
                        // This should be the beginning of the next section
                        if( (strLine.length() < 3) || (!strLine.contains( "]" )) )
                            return;   // This shouldn't happen (bad syntax).  Quit.
                        
                        x = strLine.indexOf( '[' );
                        y = strLine.indexOf( ']' );
                        
                        if( (y <= x + 1) || (x == -1) || (y == -1) )
                            return;  // This shouldn't happen (bad syntax).  Quit.
                        
                        p = strLine.substring( x + 1, y ).trim();
                        
                        // Save the name of the next section.
                        nextName = p;
                        
                        // Done reading parameters. Return.
                        return;
                    }
                    else
                    {   
                        // This shouldn't happen (bad syntax). Quit.
                        return;
                    }
                }
            }
            catch( IOException ioe )
            {
                // (Don't care)
            }
            
            // Reached end of file or error.. either way, just quit
            return;
        }
        
        /**
         * Returns a handle to the parameter keyset.
         * 
         * @return keyset containing all the parameters.
         */
        public Set<String> keySet()
        {
            return parameters.keySet();
        }
        
        /**
         * Returns the value of the specified parameter.
         * 
         * @param parameter Name of the parameter.
         * 
         * @return Parameter's value, or null if not found.
         */
        public String get( String parameter )
        {
            // Error: no parameters, or parameter was null
            if( parameters == null || TextUtils.isEmpty( parameter ) )
                return null;
            
            ConfigParameter confParam = parameters.get( parameter );
            
            // Parameter not found
            if( confParam == null )
                return null;
            
            // Got it
            return confParam.value;
        }
        
        /**
         * Adds the specified parameter to this config section, updates
         * the value if it already exists, or removes the parameter.
         * 
         * @param parameter The name of the parameter.
         * @param value     The parameter's value, or null to remove.
         */
        public void put( String parameter, String value )
        {
            ConfigParameter confParam = parameters.get( parameter );
            if( confParam == null )  // New parameter
            {
                if( !TextUtils.isEmpty( value ) )
                {
                    confParam = new ConfigParameter( parameter, value );
                    lines.add( new ConfigLine( ConfigLine.LINE_PARAM, parameter + "=" + value + "\n", confParam ) );
                    parameters.put( parameter, confParam );
                }
            }
            else
            {
                // Change the parameter's value
                confParam.value = value;
            }
        }
    
        /**
         * Writes the entire section to file.
         * 
         * @param fw File to write to.
         * 
         * @throws IOException if a writing error occurs.
         */
        public void save( FileWriter fw ) throws IOException
        {
            for (ConfigLine line : lines)
            {
                if( line != null )
                    line.save( fw );
            }
        }
    }
    
    /**
     * The ConfigLine class stores each line of the config file (including comments).
     */
    private static class ConfigLine
    {
        public static final int LINE_GARBAGE = 0;  // Comment, whitespace, or blank line
        public static final int LINE_SECTION = 1;  // Section title
        public static final int LINE_PARAM = 2;    // Parameter=value pair
    
        public int lineType = 0;                 // LINE_GARBAGE, LINE_SECTION, or LINE_PARAM.
        public String strLine = "";              // Actual line from the config file.
        public ConfigParameter confParam = null; // Null unless this line has a parameter.
        
        /**
         * Constructor: Saves the relevant information about the line.
         * 
         * @param type   The type of line.
         * @param line   The line itself.
         * @param param  Config parameters pertaining to the line.
         */
        public ConfigLine( int type, String line, ConfigParameter param )
        {
            lineType = type;
            strLine = line;
            confParam = param;
        }
        
        /**
         * Saves the ConfigLine.
         * 
         * @param fw The file to save the ConfigLine to.
         * 
         * @throws IOException If a writing error occurs.
         */
        public void save( FileWriter fw ) throws IOException
        {
            int x;
            if( lineType == LINE_PARAM )
            {
                if( !strLine.contains( "=" ) || confParam == null )
                    return;  // This shouldn't happen
                
                x = strLine.indexOf( '=' );
                
                if( x < 1 )
                    return;  // This shouldn't happen either
                
                if( x < strLine.length() )
                    fw.write( strLine.substring( 0, x + 1 ) + confParam.value + "\n" );
            }
            else
            {
                fw.write( strLine );
            }
        }
    }
    
    /**
     * The ConfigParameter class associates a parameter with its value.
     */
    private static class ConfigParameter
    {
        @SuppressWarnings( "unused" )
        public String parameter;
        public String value;
        
        /**
         * Constructor: Associate the parameter and value
         * 
         * @param parameter The name of the parameter.
         * @param value     The value of the parameter.
         */
        public ConfigParameter( String parameter, String value )
        {
            this.parameter = parameter;
            this.value = value;
        }
    }
}
