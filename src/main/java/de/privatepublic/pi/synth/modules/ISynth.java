package de.privatepublic.pi.synth.modules;

import java.nio.FloatBuffer;
import java.util.List;

public interface ISynth {

	public void process(final List<FloatBuffer> buffer, int nframes);
	
}
