package es.uned.dsaenzdec1.tfm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		generaRTTMRef,
		generaAllRTTMRef,
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
	

	public void process_subtitles_folders(String... args) {
        Path subtitlesPath = Paths.get("./data/subtitles");        
        Path rttmRefsPath = Paths.get("./data/rttm_ref");
        try {
        	if (Files.notExists(rttmRefsPath))
        		Files.createDirectories(rttmRefsPath);        
        	Files.walk(rttmRefsPath)
        		.filter(rttmFile-> rttmFile.toString().endsWith(".rttm"))
        		.forEach(rttmFile -> {
					try {
						Files.delete(rttmFile);
					} catch (IOException e) {
						e.printStackTrace();
						System.out.println(String.format("Error de entrada/salida con el path %s: %s", rttmRefsPath, e.getCause()));
					}
				});
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println(String.format("Error de entrada/salida con el path %s: %s", subtitlesPath, e.getCause()));
		}
        
        Map<String, Integer> exitCodes = new HashMap<>();
        
        try {
            Files.walk(subtitlesPath, 1)
                 .filter(subtitleFile-> subtitleFile.toString().endsWith(".srt") || subtitleFile.toString().endsWith(".vtt"))
                 .forEach(subtitleFile -> {
                	 generateOutputRttmAndExecuteCommand(rttmRefsPath, subtitleFile, exitCodes, args);
                 });
                 
        } catch (IOException e) {
            e.printStackTrace();
        }		
                
        try {
            Files.walk(subtitlesPath, 1)            	 	
            	 .filter(path -> Files.isDirectory(path) && !path.equals(subtitlesPath))
            	 .forEach(datasetPath -> {
            		try { 
            		 Files.walk(datasetPath)
	                 .filter(subtitleFile-> subtitleFile.toString().endsWith(".srt") || subtitleFile.toString().endsWith(".vtt"))
	                 .forEach(subtitleFile -> {	                	 
						 if (Files.notExists(rttmRefsPath.resolve(datasetPath.getFileName()))) {
							try {
								generateOutputRttmAndExecuteCommand(Files.createDirectory(rttmRefsPath.resolve(datasetPath.getFileName())), subtitleFile, exitCodes, args);								
							} catch (IOException e) {
								e.printStackTrace();
							}
						 }else {
							 generateOutputRttmAndExecuteCommand(rttmRefsPath.resolve(datasetPath.getFileName()), subtitleFile, exitCodes, args);
						 }
	                 });
            		}catch(IOException e) {
            e.printStackTrace();
        }		
            	});
                 
        } catch (IOException e) {
            e.printStackTrace();
        }		
	}
	
	private void generateOutputRttmAndExecuteCommand (Path rttmPath, Path subtitleFile, Map<String, Integer> exitCodes, String... args) {		
	 String outputFileName = rttmPath.resolve( Paths.get(subtitleFile.toString().substring(0, subtitleFile.toString().lastIndexOf(".")).concat(".rttm")).getFileName()).toString();
   	 List<String> argsList = new ArrayList<String>(Arrays.asList(args));
   	 argsList.add("-i=" + subtitleFile);
   	 argsList.add("-o=" + outputFileName.toString());
   	 exitCodes.put(outputFileName, new CommandLine(rttm, factory).execute(argsList.stream().toArray(String[]::new)));
	}

	@Override
	public void run(String... args) throws Exception {
		if (args[0].equals(Action.generaRTTMRef.toString()))
			exitCode = new CommandLine(rttm, factory).execute(Arrays.asList(args).stream().skip(1).toArray(String[]::new));		
		else if (args[0].equals(Action.generaAllRTTMRef.toString())) {
			process_subtitles_folders(Arrays.asList(args).stream().skip(1).toArray(String[]::new));			
		}else
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