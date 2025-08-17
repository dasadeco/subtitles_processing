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
	private EvalUneCommand evalUne;
	private int exitCode;

	private enum Action {
		generaRTTMRef, generaAllRTTMRef, evalUne, evalAllUne, aligning_sub;
	}

	@Override
	public int getExitCode() {
		return exitCode;
	}

	Application(IFactory factory, SubtitlesCommand subtCommand, RttmCommand rttmCommand, EvalUneCommand evalUne) {
		this.factory = factory;
		this.subtitles = subtCommand;
		this.rttm = rttmCommand;
		this.evalUne = evalUne;
	}

	public void process_subtitles_folders(String... args) {
		Path subtitlesPath = Paths.get("./data/subtitles");
		Path rttmRefsPath = Paths.get("./data/rttm_ref");
		Path rttmsPath = Paths.get("./media/rttm");
		Boolean generate;
		switch (Action.valueOf(args[0])) {
		case generaAllRTTMRef:
			generate = true;
			break;
		case evalAllUne:
		default:
			generate = false;
		}
		try {
			if (Files.notExists(rttmRefsPath))
				Files.createDirectories(rttmRefsPath);
			if (generate) {
				Files.walk(rttmRefsPath).filter(rttmFile -> rttmFile.toString().endsWith(".rttm")).forEach(rttmFile -> {
					try {
						Files.delete(rttmFile);
					} catch (IOException e) {
						e.printStackTrace();
						System.out.println(String.format("Error de entrada/salida con el path %s: %s", rttmRefsPath,
								e.getCause()));
					}
				});
			}

		} catch (IOException e) {
			e.printStackTrace();
			System.out
					.println(String.format("Error de entrada/salida con el path %s: %s", subtitlesPath, e.getCause()));
		}

		Map<String, Integer> exitCodes = new HashMap<>();

		try {
			if (generate) {
				Files.walk(subtitlesPath, 1).filter(subtitleFile -> subtitleFile.toString().endsWith(".srt")
						|| subtitleFile.toString().endsWith(".vtt")).forEach(subtitleFile -> {
							generateOutputRttmAndExecuteCommand(rttmRefsPath, subtitleFile, exitCodes, args);
						});
			} else {
				Files.walk(rttmsPath, 2).filter(rttmFile -> rttmFile.toString().endsWith(".rttm")).forEach(rttmFile -> {
					evalUneCommandFromSubtitleAndRttm(rttmFile, subtitlesPath, exitCodes, args);
				});
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			if (generate) {
				Files.walk(subtitlesPath, 1).filter(path -> Files.isDirectory(path) && !path.equals(subtitlesPath))
						.forEach(datasetPath -> {
							try {
								Files.walk(datasetPath).filter(subtitleFile -> subtitleFile.toString().endsWith(".srt")
										|| subtitleFile.toString().endsWith(".vtt")).forEach(subtitleFile -> {

											if (Files.notExists(rttmRefsPath.resolve(datasetPath.getFileName()))) {
												try {
													generateOutputRttmAndExecuteCommand(
															Files.createDirectory(
																	rttmRefsPath.resolve(datasetPath.getFileName())),
															subtitleFile, exitCodes, args);
												} catch (IOException e) {
													e.printStackTrace();
												}
											} else {
												generateOutputRttmAndExecuteCommand(
														rttmRefsPath.resolve(datasetPath.getFileName()), subtitleFile,
														exitCodes, args);
											}
										});
							} catch (IOException e) {
								e.printStackTrace();
							}
						});
			} else {
				Files.walk(rttmsPath, 2).filter(path -> Files.isDirectory(path) && !path.getFileName().toString().equals("rttm"))
						.forEach(datasetPath -> {
							try {
								Files.walk(datasetPath).filter(rttmFile -> rttmFile.toString().endsWith(".rttm"))
										.forEach(rttmFile -> {
											evalUneCommandFromSubtitleAndRttm(rttmFile, subtitlesPath, exitCodes, args);
										});
							} catch (IOException e) {
								e.printStackTrace();
							}
						});
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void generateOutputRttmAndExecuteCommand(Path rttmPath, Path subtitleFile, Map<String, Integer> exitCodes,
			String... args) {
		String outputFileName = rttmPath.resolve(Paths
				.get(subtitleFile.toString().substring(0, subtitleFile.toString().lastIndexOf(".")).concat(".rttm"))
				.getFileName()).toString();
		List<String> argsList = new ArrayList<String>(Arrays.asList(args));
		argsList.add("-i=" + subtitleFile);
		argsList.add("-o=" + outputFileName.toString());
		exitCodes.put(outputFileName,
				new CommandLine(rttm, factory).execute(argsList.stream().skip(1).toArray(String[]::new)));
	}
	
	/**
	 * Devuelve el path del archivo de subtitulos correspondiente a un archivo RTTM determinado
	 * @param inputRttmFile
	 * @param inputSubtitlePath
	 * @param exitCodes
	 * @return
	 */
	private Path getSubtitleFromRttm(Path inputRttmFile, Path inputSubtitlePath, Map<String, Integer> exitCodes) {
		Path datasetNamePath = inputRttmFile.getParent().getParent().getFileName();
		if (datasetNamePath.toString().equals("rttm"))
			datasetNamePath = Paths.get(".");
		String fileNameSubtitle = Paths.get(inputRttmFile.toString().substring(0, inputRttmFile.toString().lastIndexOf(".")).concat(".srt")).getFileName().toString();
		Path inputSubtitleFile = inputSubtitlePath.resolve( Paths.get(datasetNamePath.toString(), fileNameSubtitle));
		if (Files.notExists(inputSubtitleFile)) {  
			inputSubtitleFile = inputSubtitlePath.resolve( Paths.get(datasetNamePath.toString(), fileNameSubtitle.replaceAll("_", " ")));
			if (Files.notExists(inputSubtitleFile)) {								
				fileNameSubtitle = Paths.get(inputRttmFile.toString().substring(0, inputRttmFile.toString().lastIndexOf(".")).concat(".vtt")).getFileName().toString();				
				inputSubtitleFile = inputSubtitlePath.resolve(Paths.get(datasetNamePath.toString(), fileNameSubtitle));
				if (Files.notExists(inputSubtitleFile)) { 
					inputSubtitleFile = inputSubtitlePath.resolve( Paths.get(datasetNamePath.toString(), fileNameSubtitle.replaceAll("_", " ")));
				}if (Files.notExists(inputSubtitleFile)) {
					return null;
				}
			}
		}				
		return inputSubtitleFile;
	}
		
	/**
	 * Devuelve el path del archivo RTTM de subtitulos correspondiente a un archivo de subtítulos determinado
	 * @param inputRttmFile
	 * @param inputSubtitlePath
	 * @param exitCodes
	 * @return
	 */
	private Path getRttmFromSubtitle(Path inputSubtitleFile, Path inputRttmPath, Map<String, Integer> exitCodes) {
		Path datasetNamePath = inputSubtitleFile.getParent().getFileName();
		if (datasetNamePath.toString().equals("subtitles"))
			datasetNamePath = Paths.get(".");
		String fileNameRttm = Paths.get(inputSubtitleFile.toString().substring(0, inputSubtitleFile.toString().lastIndexOf(".")).concat(".rttm")).getFileName().toString();
		Path inputRttmFile = inputRttmPath.resolve( Paths.get(datasetNamePath.toString(), fileNameRttm));
		if (Files.notExists(inputRttmFile)) {  
			inputRttmFile = inputRttmPath.resolve( Paths.get(datasetNamePath.toString(), fileNameRttm.replaceAll(" ", "_")));
			if (Files.notExists(inputRttmFile)) {			
					return null;
			}			
		}				
		return inputRttmFile;
	}		

	private void evalUneCommandFromSubtitleAndRttm(Path inputRttmFile, Path inputSubtitlePath, Map<String, Integer> exitCodes, String... args) {
		List<String> argsList = new ArrayList<String>(Arrays.asList(args));
		Path inputSubtitleFile = getSubtitleFromRttm(inputRttmFile, inputSubtitlePath, exitCodes);		
		if (inputSubtitleFile!=null) {
			argsList.add("-is=" + inputSubtitleFile);
			if (exitCodes.get(inputSubtitleFile.toString())!= null && exitCodes.get(inputSubtitleFile.toString())==2) {
				argsList.add("-lab=true");
			}
		}
		argsList.add("-ir=" + inputRttmFile.toString());
		
		int code = new CommandLine(evalUne, factory).execute(argsList.stream().skip(1).toArray(String[]::new));
		if (inputSubtitleFile!=null)
			exitCodes.put(inputSubtitleFile.toString(), code);
		else
			exitCodes.put(inputRttmFile.toString(), code);						
	}

	@Override
	public void run(String... args) throws Exception {
		switch (Action.valueOf(args[0])) {
			case generaRTTMRef:
				exitCode = new CommandLine(rttm, factory)
						.execute(Arrays.asList(args).stream().skip(1).toArray(String[]::new));
				break;
			case aligning_sub:
				exitCode = new CommandLine(subtitles, factory)
						.execute(Arrays.asList(args).stream().skip(1).toArray(String[]::new));
				break;
			case evalUne:	
				exitCode = new CommandLine(evalUne, factory).execute(Arrays.asList(args).stream().skip(1).toArray(String[]::new));
				break;
			case generaAllRTTMRef:
				process_subtitles_folders(Arrays.asList(args).stream().toArray(String[]::new));
				break;				
			case evalAllUne:
				process_subtitles_folders(Arrays.asList(args).stream().toArray(String[]::new));
				break;
			default:
				exitCode = new CommandLine(subtitles, factory).execute(args);
		}
	}

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		String[] filteredArgs = Arrays.asList(args).stream()
				.filter(arg -> !arg.equals("--debug") && !arg.startsWith("--spring.")).toArray(String[]::new);
		System.exit(SpringApplication.exit(SpringApplication.run(Application.class, filteredArgs)));
	}

}