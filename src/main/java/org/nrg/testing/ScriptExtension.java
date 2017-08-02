package org.nrg.testing;

import org.nrg.testing.file.FileIO;
import org.nrg.xnat.enums.ScriptLocation;
import org.nrg.xnat.pojo.AnonScript;
import org.nrg.xnat.pojo.extensions.AnonScriptExtension;

import java.io.File;

public class ScriptExtension extends AnonScriptExtension {

    private File file;

    public ScriptExtension(AnonScript script, File file) {
        super(script, ScriptLocation.FILE, null);
        this.file = file;
    }

    public ScriptExtension(File file) {
        super();
        this.file = file;
        setLocation(ScriptLocation.FILE);
    }

    @Override
    public String readScriptFromFile() {
        return FileIO.readFile(file);
    }

    @Override
    public String readScriptFromURL() {
        throw new UnsupportedOperationException("This extension can only read from file.");
    }

}
