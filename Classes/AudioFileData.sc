AudioFileData {

    var sf, server;
    var wasOpen;

    *new {|aSoundFile, server|
        ^super.newCopyArgs(aSoundFile, server ? Server.default).init;
    }

    init {
        wasOpen = false;
        this.open;
    }

    getRandomChunk {|duration, offset=0, numChannels, fadeTime=0|
        var start;
        this.open;
        ^this.getChunk(duration, rrand(offset, sf.duration), numChannels, fadeTime);
    }

    getChunk {|duration, offset=0, numChannels, fadeTime=0|
        var buf, end, len;
        var rawData, rawData1, tmpPath, tmpSf;
        var framesToRead, framesToIndex, channelsToRead, offsetToRead, offsetToIndex;
        this.open;
        // read number of channels from file if not told otherwise
        numChannels    = numChannels ? sf.numChannels;
        channelsToRead = numChannels.collect{|x| x };
        // convert duration to frames
        framesToRead   = (duration * sf.sampleRate * numChannels).floor.asInt;
        framesToIndex  = (duration * sf.sampleRate * sf.numChannels).floor.asInt;
        offsetToRead   = (offset * sf.sampleRate * numChannels).floor.asInt;
        offsetToIndex  = (offset * sf.sampleRate * sf.numChannels).floor.asInt;
        // calculate positions
        end = offsetToIndex + framesToIndex;
        len = sf.numFrames * sf.numChannels;
        // wrap around the file if duration goes above len
        if(framesToIndex > len or:{end >= len}) {
            rawData = FloatArray.newClear(len);
            sf.readData(rawData);
            rawData1 = this.concatenateData(rawData, sf.sampleRate, numChannels, framesToIndex);
            rawData1 = rawData1[offsetToIndex..(end-1)];
            tmpPath  = PathName.tmp ++ sf.hash.asString;
            tmpSf    = SoundFile();
            tmpSf.sampleRate  = sf.sampleRate;
            tmpSf.numChannels = sf.numChannels;
            // write the looped chunk data
            if(tmpSf.openWrite(tmpPath)) {
                tmpSf.writeData(rawData1);
                tmpSf.close;
                buf = Buffer.readChannel(server, tmpPath, 0, -1, channels:channelsToRead, action:{|b|
                    if(File.delete(tmpPath).not) { 
                        ("Could not delete tmp file:" + tmpPath).warn 
                    };
                });
            } {
                ("Failed to write data to tmp file:" + tmpPath).warn;
            };
        } {
            channelsToRead = numChannels.collect{|x| x };
            buf = Buffer.readChannel(server, sf.path, offsetToRead, framesToRead, channels:channelsToRead);
        };
        server.sync;
        if(fadeTime > 0 ) {
            if(fadeTime <= ((buf.numFrames*buf.numChannels)/buf.sampleRate)) {
                buf.loadToFloatArray(action:{|a| rawData = this.applyWindow(a, fadeTime, sf.sampleRate, numChannels) });
                server.sync;
                buf = Buffer.loadCollectionWithSampleRate(server, rawData, numChannels, sf.sampleRate);
            } {
                "'fadeTime' needs to be less than or equal to chunk duration. No window was applied.".warn;
            }
        };
        this.close;
        ^buf;
    }

    concatenateData {|floatArray, sampleRate, numChannels, numFrames|
        var xfadeDur = 1/20;
        var fadeFrames, rawData, rawSize;
        var segment, xfade, loop, numIterations;
        // length of crossfade
        fadeFrames  = (xfadeDur * sampleRate * numChannels).floor.asInt;
        // apply window
        rawData = this.applyWindow(floatArray, xfadeDur, sampleRate, numChannels);
        rawSize = rawData.size;
        // calculate crossfade
        xfade = rawData[..(fadeFrames-1)] + rawData[((rawSize-1) - (fadeFrames-1))..];
        // signal w/o fades
        segment = rawData[fadeFrames..((rawSize-1) - fadeFrames)];
        // see how many times we need to loop
        numIterations = (rawSize / numFrames).reciprocal.roundUp(1).asInt;
        loop = xfade ++ segment;
        numIterations.do { loop = loop ++ loop };
        ^loop.as(FloatArray);
    }

    applyWindow {|floatArray, duration, sampleRate, numChannels|
        var win, fadeFrames, fadeFrames1, fadeIn, fadeOut, len;
        fadeFrames = (duration * sampleRate * numChannels).floor.asInt;
        win        = Signal.hanningWindow(2*fadeFrames);
        fadeIn     = win[..(fadeFrames-1)];
        fadeOut    = win[fadeFrames..];
        len        = floatArray.size;
        fadeIn.do  {|x,i| floatArray[i] = x * floatArray[i] };
        fadeOut.do {|x,i| 
            var offset = (len-1) - (fadeFrames-1);
            floatArray[offset+i] = x * floatArray[offset+i] 
        };
        ^floatArray;
    }

    open {
        if(sf.isOpen.not) { sf.openRead } { wasOpen = true };
    }

    close {
        if(sf.isOpen and:{wasOpen.not}) { sf.close; wasOpen = false; };
    }
}
