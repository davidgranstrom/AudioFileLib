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
        library = ();
        libraryWithFileNames = ();
        if(path.last == $/) {
            path = path.drop(-1);
        };
        this.populateLibrary(PathName(path));
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

    populateLibrary {|pn|
        var key, traverseSubdir;
        traverseSubdir = {|pn, parentPath|
            var key = pn.folderName;
            key = (parentPath.asString +/+ key).asSymbol;
            library.put(key, List[]);
            libraryWithFileNames.put(key, ());
            pn.files.do {|f|
                this.validateFile(f, key)
            };
            if(pn.folders.isEmpty.not) {
                pn.folders.do {|p|
                    traverseSubdir.(p, key)
                };
            }
        };
        // start with top-level dir
        key = pn.fileName.asSymbol;
        library.put(key, List[]);
        libraryWithFileNames.put(key, ());
        pn.entries.do {|f|
            if(f.isFolder.not) {
                this.validateFile(f, key);
            } {
                traverseSubdir.(f, key);
            }
        };
        // get rid of empty entries
        library.keys.do {|k|
            if(library[k].isEmpty) {
                library.removeAt(k);
                libraryWithFileNames.removeAt(k);
            }
        }
    }

    validateFile {|f, key|
        var ext, result, sf;
        var validExtensions = "AIFF, AIFC, RIFF, WAVEX, WAVE, WAV, Sun, 
        IRCAM, NeXT, raw, MAT4, MAT5, PAF, SVX, NIST, VOC, W64, PVF, 
        XI, HTK, SDS, AVR, SD2, FLAC, vorbis, CAF, RF64";
        ext    = f.extension;
        result = validExtensions.containsi(ext);
        if(result) {
            sf = SoundFile.openRead(f.absolutePath);
            library[key].add(sf);
            libraryWithFileNames[key].put(
                f.fileNameWithoutExtension.asSymbol,
                sf
            );
            sf.close;
        }
    }

    files {
        var l;
        if(library.isEmpty.not) {
            l = List[];
            library.collect(l.add(_));
            ^l.flat;
        } {
            "No files in library.".warn;
        }
    }

    libraryWithBuffers {|withFileNames=false|
        var lib;
        if(withFileNames.not) {
            lib = library.copy;
        } {
            lib = libraryWithFileNames.copy;
        };
        ^lib.keysValuesChange {|key, val|
            val.collect {|sf|
                if(sf.openRead) {
                    sf.asBuffer;
                } {
                    "Could not open SoundFile".throw;
                }
            };
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
                    Post << "\t" << "File name   : " << PathName(x.path).fileName << "\n"
                    << "\t" << "Channels    : " << x.numChannels << "\n"
                    << "\t" << "Sample Rate : " << x.sampleRate << "\n"
                    << "\t" << "Duration    : " << x.duration.asTimeString << "\n" << "\n";
                }
            }
        } {
            "No files in library.".warn;
        }
    }

    purge {
        library = ();
        libraryWithFileNames = ();
    }
}
