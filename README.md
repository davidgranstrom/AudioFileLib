AudioFileLib
============

Be able to store and write libraries of audio files.

AudioFileLib recursively adds valid audio files from a directory on disk storing them in a dictionary. It uses the directory name as the key and an (unordered) list of audio files as its value, subdirectories will be indexed using its parent folder as prefix (parent/child).
