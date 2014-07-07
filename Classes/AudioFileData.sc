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
            rawData1 = this.concatenateData(rawData, sf.sampleRate, offsetToIndex, end);
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

    concatenateData {|floatArray, sr, startFrame, endFrame|
        var xfadeDur  = 1/20;
        var wsig, sig = floatArray;
        var slope, fadeIn, fadeOut;
        var fadeFrames, fadeFrames1;
        var len, segment, xfade, loop, numIterations;
        // length of crossfade
        fadeFrames  = (xfadeDur * sr).floor.asInt;
        fadeFrames1 = fadeFrames - 1;
        // we only need to calculate the slopes of the window
        slope   = fadeFrames.collect {|i| cos((i/(fadeFrames1)) * pi).madd(0.5,0.5) };
        fadeIn  = ((-1*slope)+1);
        fadeOut = slope;
        wsig    = sig;
        // apply window
        sig[..fadeFrames1].do {|smp,i| wsig[i] = smp * fadeIn[i] }; 
        sig[((sig.size-1)-fadeFrames1)..].do {|smp,i| wsig[i + ((sig.size-1)-fadeFrames1)] = smp * fadeOut[i] }; 
        // calculate crossfade
        xfade   = wsig[..fadeFrames1] + wsig[((wsig.size-1) - fadeFrames1)..];
        // signal w/o fades
        segment = wsig[fadeFrames..((wsig.size-1) - fadeFrames)];
        // see how many times we need to loop
        len = endFrame - startFrame;
        numIterations = (wsig.size / len).reciprocal.roundUp(1).asInt;
        loop = xfade ++ segment;
        numIterations.do { loop = loop ++ loop };
        ^loop.as(FloatArray);
    }

    applyWindow {|floatArray, duration, sampleRate, numChannels|
        var win, fadeFrames, fadeFrames1, fadeIn, fadeOut, len;
        fadeFrames = duration * sampleRate * numChannels;
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
