// =============================================================================
// Title         : AudioFileLib
// Description   : Audio file library
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
        var stamp = "diflib_" ++ Date.getDate.stamp;
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

    print {
        if(library.isEmpty.not) {
            library.keysValuesDo {|key, val| 
                "o- NAME: \"%\"\n".postf(key);
                val.asSortedArray.do {|x|
                    if(x[0] == 'duration') {
                        x[1] = x[1].asTimeString;
                    };
                    "|_ %: %\n".postf(x[0], x[1]);
                };
                Post << Char.nl;
            }
        } {
            "No files in library.".postln;
        }
    }

    purge {
        library = ();
    }
}
