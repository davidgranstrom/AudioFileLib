// =============================================================================
// Title         : AudioFileLib
// Description   : Store and load audio file libraries from disk.
// Copyright (c) : David Granstrom 2014 
// =============================================================================

AudioFileLib {

    var <library, <libraryWithFileNames;

    *new {|path|
        ^super.new.initWithPath(path);
    }

    *newFromFile {|path|
        ^super.new.initWithFile(path);
    }

    initWithPath {|path|
        var pn = PathName(path);
        library = ();
        libraryWithFileNames = ();
        if(pn.folders.isEmpty) {
            this.populateLibrary(pn);
        } {
            this.populateLibraryRecursively(pn);
        }
    }

    initWithFile {|path|
        library = ();
        libraryWithFileNames = ();
        this.load(path);
    }

    load {|path|
        var lib;
        try { lib = Object.readArchive(path) } {
            "Could not load library".throw;
        };
        library = lib;
        // TODO: recreate libraryWithFileNames
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

    populateLibraryRecursively {|pn, parentPath|
        pn.folders.do {|folder|
            var key = folder.folderName.asSymbol;
            if(parentPath.notNil) {
                key = (parentPath +/+ key).asSymbol;
            };
            library.put(key, List[]);
            libraryWithFileNames.put(key, ());
            // add valid audio files to the library
            folder.files.do {|f| this.validateFile(f, key) };
            // traverse all sub-folders
            if(folder.folders.isEmpty.not) {
                this.populateLibraryRecursively(folder, folder.folderName);
            }
        };
        library.sort; // sort alphabetically
    }

    populateLibrary {|pn|
        var key = pn.fileName;
        library.put(key, List[]);
        libraryWithFileNames.put(key, ());
        // add valid audio files to the library
        pn.files.do {|f|
            this.validateFile(f, key);
        };
        library.sort; // sort alphabetically
    }

    validateFile {|f, key|
        var ext, result, sf;
        var validExtensions = "AIFF, AIFC, RIFF, WAVEX, WAVE, WAV, Sun, 
        IRCAM, NeXT, raw, MAT4, MAT5, PAF, SVX, NIST, VOC, W64, PVF, 
        XI, HTK, SDS, AVR, SD2, FLAC, vorbis, CAF, RF64";
        ext    = f.extension;
        result = validExtensions.containsi(ext);
        if(result) {
            sf = SoundFile(f.absolutePath);
            library[key].add(sf);
            libraryWithFileNames[key].put(
                f.fileNameWithoutExtension.asSymbol,
                sf
            );
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
