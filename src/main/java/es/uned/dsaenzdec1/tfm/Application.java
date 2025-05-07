package es.uned.dsaenzdec1.tfm;

import java.util.Arrays;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

@SpringBootApplication
public class Application implements CommandLineRunner, ExitCodeGenerator {
	
	private IFactory factory;
	private SubtitlesCommand subtitles;
	private RttmCommand rttm;
	private int exitCode;
	
	private enum Action {
		diarization,
		aligning_sub;		
	}	
	
	@Override
	public int getExitCode() {					
		return exitCode;
	}		
	
	Application(IFactory factory, SubtitlesCommand subtCommand, RttmCommand rttmCommand){
		this.factory = factory;
		this.subtitles = subtCommand;
		this.rttm = rttmCommand;
	}

	@Override
	public void run(String... args) throws Exception {
		if (args[0].equals(Action.diarization.toString()))
			exitCode = new CommandLine(rttm, factory).execute(Arrays.asList(args).stream().skip(1).toArray(String[]::new));			
		else
			if (args[0].equals(Action.aligning_sub.toString()))
				exitCode = new CommandLine( subtitles, factory).execute(Arrays.asList(args).stream().skip(1).toArray(String[]::new));
			else
				exitCode = new CommandLine( subtitles, factory).execute(args);				
	}
		
	/**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
    	String[] filteredArgs = Arrays.asList(args).stream().filter(arg -> !arg.equals("--debug") && !arg.startsWith("--spring.")).toArray(String[]::new);
    	System.exit(SpringApplication.exit(SpringApplication.run(Application.class, filteredArgs)));
    }	
	
}