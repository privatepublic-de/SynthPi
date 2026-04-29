#SynthPi
Open source virtual analogue, additive organ and Karplus-Strong synthesizer engine in Java.

All documentation and the user manual can be found here: [http://www.privatepublic.de/blog/software/synthpi-synthesizer/](http://www.privatepublic.de/blog/software/synthpi-synthesizer/).

SynthPi uses and is grateful for the following open source components and resources:

* JAudioLibs (by Neil C. Smith) including Audio Ops Freeverb implementation: [https://github.com/jaudiolibs](https://github.com/jaudiolibs)
* Lots of inspiration there: [http://www.musicdsp.org](http://www.musicdsp.org)
* AKWF Waveforms (by Kristoffer Ekstrand aka. Adventure Kid) for strings, keyboards and vocal waveform sets: [http://www.adventurekid.se/akrt/waveforms](http://www.adventurekid.se/akrt/waveforms) 
* [jQuery](https://jquery.com), [jQuery-color](https://github.com/jquery/jquery-color), [jQuery-knob by Anthony Terrien](https://github.com/aterrien/jQuery-Knob) 
* Jetty web server and websockets: [http://www.eclipse.org/jetty](http://www.eclipse.org/jetty)
* some Apache Commons Java utils: [https://commons.apache.org](https://commons.apache.org)
* Logback logging framework: [http://logback.qos.ch](http://logback.qos.ch) 
* Java JSON processing classes: [http://json.org](json.org) 
* Google Material Design Icons: [https://github.com/google/material-design-icons](https://github.com/google/material-design-icons)

Build with `mvn package`. All dependencies (including JAudioLibs `audioservers` and `jnajack`) resolve from Maven Central — no manual installation step required.
