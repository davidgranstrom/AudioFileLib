+ Buffer {
	// transfer a collection of numbers to a buffer through a file
	*loadCollectionWithSampleRate { arg server, collection, numChannels = 1, sampleRate, action;
		var data, sndfile, path, bufnum, buffer;
		server = server ? Server.default;
		bufnum = server.bufferAllocator.alloc(1);
		if(bufnum.isNil) {
			Error("No more buffer numbers -- free some buffers before allocating more.").throw
		};
		server.isLocal.if({
			if(collection.isKindOf(RawArray).not) { collection = collection.as(FloatArray) };
			sndfile = SoundFile.new;
			sndfile.sampleRate = sampleRate ? server.sampleRate;
			sndfile.numChannels = numChannels;
			path = PathName.tmp ++ sndfile.hash.asString;
			if(sndfile.openWrite(path),
				{
					sndfile.writeData(collection);
					sndfile.close;
					^super.newCopyArgs(server, bufnum)
						.cache.doOnInfo_({ |buf|
							if(File.delete(path), { buf.path = nil},
								{("Could not delete data file:" + path).warn;});
							action.value(buf);
						}).allocRead(path, 0, -1,{|buf|["/b_query",buf.bufnum]});

				}, {"Failed to write data".warn; ^nil}
			);
		}, {"cannot use loadCollection with a non-local Server".warn; ^nil});
	}
}
