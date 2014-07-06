AudioFileData {

    var sf, server;
    var wasOpen;

    *new {|aSoundFile, server|
        ^super.newCopyArgs(aSoundFile, server ? Server.default).init;
    }

    init {
        wasOpen = false;
        if(sf.isOpen.not) { sf.openRead } { wasOpen = true };
    }

    calcFrames {|n|
        ^(n * sf.sampleRate * sf.numChannels).floor.asInt;
    }

    getRandomChunk {|duration, offset=0, numChannels|
        var start, eof, ofx;
        start = this.calcFrames(offset);
        eof   = (sf.numFrames * sf.numChannels).asInt;
        ofx   = rrand(offset, eof - 1);
        ^this.getChunk(duration, ofx, numChannels);
    }

    getChunk {|duration, offset=0, numChannels|
        var buf, end, len;
        var rawData, rawData1;
        var framesToRead, framesToIndex, channelsToRead;
        // read number of channels from file if not told otherwise
        numChannels = numChannels ? sf.numChannels;
        // convert duration to frames
        framesToRead  = (duration * sf.sampleRate).floor.asInt;
        framesToIndex = (duration * sf.sampleRate * sf.numChannels).floor.asInt;
        // calculate positions
        end = offset + framesToIndex;
        len = sf.numFrames * sf.numChannels;
        // wrap around the file if duration goes above len
        if(framesToIndex > len or:{end > len}) {
            rawData = FloatArray.newClear(len);
            sf.readData(rawData);
            rawData1 = this.concatenateData(rawData, sf.sampleRate, offset, end);
            rawData1 = rawData1[offset..(end-1)];
            if(server == Server.local) {
                buf = Buffer.loadCollection(server, rawData1, numChannels);
            } {
                buf = Buffer.sendCollection(server, rawData1, numChannels, wait: -1);
            };
        } {
            channelsToRead = numChannels.collect{|x| x };
            buf = Buffer.readChannel(server, sf.path, offset, framesToRead, channels:channelsToRead);
        };
        this.cleanup;
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

    cleanup {
        if(sf.isOpen and:{wasOpen.not}) { sf.close; wasOpen = false; };
    }
}
