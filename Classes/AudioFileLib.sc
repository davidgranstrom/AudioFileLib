// =============================================================================
// Title         : AudioFileLib
// Description   : Store and load audio file libraries from disk.
// Copyright (c) : David Granstrom 2014 
// =============================================================================

AudioFileLib {

    var <library;

    *new {|path|
        ^super.new.initWithPath(path);
    }

    *newFromFile {|path|
        ^super.new.initWithFile(path);
    }

    initWithPath {|path|
        library = ();
        this.populateLibrary(PathName(path));
    }

    initWithFile {|path|
        library = ();
        this.load(path);
    }

    load {|path|
        var lib;
        try { lib = Object.readArchive(path) } {
            "Could not load library".throw;
        };
        library = lib;
        "Loaded library.".postln;
    }

    save {|path|
        var stamp = "audiofilelib_" ++ Date.getDate.stamp;
        path = path ?? { Platform.userAppSupportDir +/+ stamp }; 
        try { library.writeArchive(path) } {
            "Could not write file to %.\nCheck your disk permissions.".format(path).throw;
        };
        "Saved library to %\n.".postf(path);
    }

    populateLibrary {|pn, parentPath|
        var result, ext;
        var validExtension = "wav, aiff";
        pn.folders.do {|folder|
            var key = folder.folderName.asSymbol;
            if(parentPath.notNil) {
                key = (parentPath +/+ key).asSymbol;
            };
            library.put(key, List[]);
            // add any valid audio files to the path entry
            folder.files.do {|f|
                ext    = f.extension;
                result = validExtension.containsi(ext);
                // check if file is valid
                if(result) {
                    library[key].add(SoundFile(f.absolutePath));
                }
            };
            // traverse all sub-folders
            if(folder.folders.isEmpty.not) {
                this.populateLibrary(folder, folder.folderName);
            }
        }
    }

    files {
        var l;
        if(library.isEmpty.not) {
            l = List[];
            library.collect(l.add(_));
            ^l.flat;
        } {
            "No files in library.".postln;
        }
    }

    find {|str|
        ^this.files.collect {|sf|
            var path = PathName(sf.path);
            if(path.fileName.containsi(str)) { sf }  
        }.reject {|x| x.isNil };
    }

    print {
        if(library.isEmpty.not) {
            library.keysValuesDo {|key, val| 
                Post << key << "\n" << "\n";
                val.do {|x|
                    var dur, wasOpen = false;
                    // open the SoundFile so we can read the duration
                    if(x.isOpen.not) { x.openRead } { wasOpen = true };
                    Post << "\t" << "File name   : " << PathName(x.path).fileName << "\n"
                    << "\t" << "Channels    : " << x.numChannels << "\n"
                    << "\t" << "Sample Rate : " << x.sampleRate << "\n"
                    << "\t" << "Duration    : " << x.duration.asTimeString << "\n" << "\n";
                    // maybe close the SoundFile
                    if(x.isOpen and:{wasOpen.not}) { x.close };
                }
            }
        } {
            "No files in library.".postln;
        }
    }

    purge {
        library = ();
    }
}
