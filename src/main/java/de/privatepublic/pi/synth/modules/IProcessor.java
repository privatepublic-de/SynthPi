package de.privatepublic.pi.synth.modules;

public interface IProcessor {

	public void controlStep();
	
	public float sampleStep(float sample);
	
}
