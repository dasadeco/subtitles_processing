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
	private int exitCode;
	
	@Override
	public int getExitCode() {					
		return exitCode;
	}		
	
	Application(IFactory factory, SubtitlesCommand command){
		this.factory = factory;
		this.subtitles = command;
	}

	@Override
	public void run(String... args) throws Exception {
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