package es.uned.dsaenzdec1.tfm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import es.uned.dsaenzdec1.tfm.helper.LabelerHelper;
import es.uned.dsaenzdec1.tfm.helper.LabelerHelper.ParsedRttmLine;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name="Evalúa diarización según norma UNE", version="1.0", mixinStandardHelpOptions = true, description="Evalúa los puntos 4.5, 6.4, 6.7 de la norma UNE 153010:2012 asignando a cada archivo"
		+ " y a cada punto un valor entre 0(peor) y 1(mejor), genera un archivo CSV con los resultados ")
public class EvalUneCommand implements Callable<Integer> {

	final static String WEBVTT = "WEBVTT";
	final static String UNE_METRICS_FILE = "UNE_METRICS.txt";
	
	final static long SHORT_INTERVAl = 3000L;
	
	@Option(names = { "-is", "--input-subtitle" }, paramLabel = "ARCHIVO DE SUBTITULOS EVALUABLE", description = "Archivo con subtitulos a evaluar según UNE")
    File subtitleIn;
	
	@Option(names = { "-ir", "--input-rttm" }, paramLabel = "ARCHIVO RTTM DE APOYO", description = "Archivo Rich Time de apoyo a la evaluación de un subtítulo")
    File rttmIn;
	
	@Option(names = { "-lab", "--labeled" }, paramLabel = "ARCHIVO DE SUBTITULOS ETIQUETADO ?", description = "Valor Booleano (True/False) que nos indica si el archivo de subtítulos ya está etiquetado ")
	@Getter
	Boolean labeled;
	
	@Option(names = { "-6.7", "--une_6.7" }, paramLabel = "SE ETIQUETA CON ABREVIATURAS ?", defaultValue = "true", description = "Valor Booleano (True/False) que nos indica si en caso de tener que etiquetar el archivo de subtítulos, hay que cumplir la norma de usar abreviaturas a partir de la segunda aparición de un hablante.")
	Boolean cumplir6_7;	

    //@Option(required = true, names = { "-d", "--delta" }, paramLabel = "PARAM. DE HOLGURA", defaultValue = "1000", description = "Hiperparametro para holgura en la comparación de intervalos")  
	//Long deltaHiper;	
	
	private static Pattern rangeTimePattern = Pattern.compile("(\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})(\\s-->\\s)(\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})");
	private static Pattern speakerPattern = Pattern.compile("^\\([A-Zaa-z0-9\\s\\-_\\,\\.\\:]+\\).*$");
	private static Pattern sentenceWithoutSpeakerLabeledPattern = Pattern.compile("^(?!\\()[A-Za-zÑñ\\sá-úà-ùä-üâ-ûÁ-ÚÀ-ÙÄ-ÜÂ-Û[0-9]\\.,;\\:\\-\\?¿\\!¡\\\"\\'\\`\\*\\+]+$");
	private static Pattern twoSpeakersInOneLine_4_5_Pattern = Pattern.compile("^\\([A-Za-z0-9\\s\\-_\\,\\.\\:]+\\).*\\([A-Za-z0-9\\s\\-_\\,\\.\\:]+\\).*$");	  

	private static Integer OK = 0, KO = 1;
	private enum SpeakersInInterval{
		None, One, More
	}
	
	private LocalTime currentRttmLocalTimeBegin;
	private LocalTime currentRttmLocalTimeEnd;
	
	private String previousSpeaker;
	private String currentSpeaker;

	private String filenameWithoutExtension;
	private File filenameOut;

	final static String NARRADOR = "NARRADOR";
	private static final boolean APPEND = Boolean.TRUE;

	private Boolean lastIntervalIsShort = null;
	private SpeakersInInterval speakerFound = SpeakersInInterval.None;
	private Boolean notFirstIntervalTime = Boolean.FALSE;
	private Boolean firstSpeaker = Boolean.FALSE;
	
	@Autowired
	private LabelerHelper labelerHelper;
	
	
	public SpeakersInInterval getSpeakerFound() {
		return speakerFound;
	}

	public void setSpeakerFound(SpeakersInInterval speakerFound) {
		this.speakerFound = speakerFound;
	}

	private void initializeProperties() {
		this.currentRttmLocalTimeBegin = null;
		this.currentRttmLocalTimeEnd = null;
		this.previousSpeaker = null;
		this.currentSpeaker = null;
		this.firstSpeaker = Boolean.FALSE;
		this.filenameWithoutExtension = null;
	}
	
	public Boolean getLastIntervalIsShort() {
		return lastIntervalIsShort;
	}
	
	public void setLastIntervalIsShort(Boolean isShort) {
		lastIntervalIsShort = isShort;
	}	
			
	/**
	 * Tras leer una linea del archivo de subtítulos que sigue el patrón (\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})(\\s-->\\s)(\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})
	 * Obtenemos las dos marcas de tiempo devolviendo como resultado si la entrada es considerada "corta"  
	 * @param timeStrBegin
	 * @param timeStrEnd
	 * @param bufferWriter
	 * @param lineInt
	 * @throws IOException
	 */
    private void evalNewInterval(String timeStrBegin, String timeStrEnd, int lineInt) throws IOException {    	
		DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("HH:mm:ss" + timeStrBegin.charAt(8) + "SSS");
    	if ( StringUtils.isNotBlank(timeStrBegin) && StringUtils.isNotBlank(timeStrEnd)) {         			
    		this.currentRttmLocalTimeBegin = LocalTime.parse(timeStrBegin, dtFormatter);
    		this.currentRttmLocalTimeEnd = LocalTime.parse(timeStrEnd, dtFormatter);    		
    		setLastIntervalIsShort(this.currentRttmLocalTimeBegin.until(currentRttmLocalTimeEnd, ChronoUnit.MILLIS ) < SHORT_INTERVAl);
    	}else {
    		throw new RuntimeException(String.format("No hay timestamp de fin en la linea %d", lineInt));
    	}

	}	
    
    private List<String> splitRttmPath(){    	
    	String rttmFileName = rttmIn.getName();
    	String modelsFolder = rttmIn.toPath().getParent().getFileName().toString();
    	String datasetFolder = rttmIn.toPath().getParent().getParent().getFileName().toString().equals("rttm") ? "." : rttmIn.toPath().getParent().getParent().getFileName().toString();
    	return Arrays.asList(rttmFileName, modelsFolder, datasetFolder);
    }
	
    /**
     * Realiza la escasa evaluación posible de un punto de la norma UNE a partir unicamente del archivo RTTM  
     * @param bufferRttmIn
     * @param bufferUneWriter
     * @return
     */
    public int evalUNEFromRttm(BufferedReader bufferRttmIn, BufferedWriter bufferUneWriter) {
    	Float une4_5 = null, une6_4 = null, une6_7 = null;
    	une4_5 = eval4_5FromRttm(bufferRttmIn);
    	une6_4 = eval6_4FromRttm(bufferRttmIn);
		une6_7 = eval6_7FromRttm(bufferRttmIn, bufferUneWriter);
		try {
			List<String> splittedPath = splitRttmPath();
			String spaceJoinedPath = splittedPath.stream().collect(Collectors.joining(" "));
			bufferUneWriter.write(new StringBuilder(spaceJoinedPath).append(" ")
					.append("une45=").append(une4_5==null ? "NA" : une4_5.toString()).append(" ")
					.append("une64=").append(une6_4==null ? "NA" : une6_4.toString()).append(" ")
					.append("une67=").append(une6_7==null ? "NA" : une6_7.toString())
					.toString());
			bufferUneWriter.newLine();
		} catch (IOException e) {
			e.printStackTrace();
			return 1;
		}		
		return 0;
    }	
    
	
	private Float eval4_5FromRttm(BufferedReader bufferRttmIn) {
		//throw new NotImplementedException();
		return null;
	}    

	
	private Float eval6_4FromRttm(BufferedReader bufferRttmIn) {
		//throw new NotImplementedException();
		return null;
	}	
	
	private Float eval6_7FromRttm(BufferedReader bufferRttmIn, BufferedWriter bufferUneWriter) {
		float une6_7 = 0.0f;
		try {
			labelerHelper.parseAllRttmFile(bufferRttmIn);
			List<ParsedRttmLine> rttmLinesList = labelerHelper.getParsedRttmLinesList();
			Map<String, Integer> apariciones = new HashMap<>();
			rttmLinesList.stream().forEach(prl -> 
				{
					if (apariciones.containsKey(prl.getSpeaker())) {
						apariciones.put(prl.getSpeaker(), apariciones.get(prl.getSpeaker())+1 );
					}else
						apariciones.put(prl.getSpeaker(), 1);							
				});
			int npit = 0, npieca = 0;
			for (Entry<String, Integer> entry : apariciones.entrySet()){				
				if (entry.getKey().length()>2) { //No son abreviaturas
					npit += entry.getValue()-1; 
				}else { // Son abreviaturas
					npit += entry.getValue();
					npieca += entry.getValue();
				}
			}
			une6_7 = (float)npieca / (float)npit;						
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Se ha producido una excepción al parsear el archivo Rttm " + rttmIn);
			System.exit(1);
		}
		return une6_7;		
	}


	public int evalUNEFromSubtitle(BufferedReader bufferSubtitleIn, BufferedWriter bufferUneWriter) {
		Float une4_5 = eval4_5FromSubtitle(bufferSubtitleIn, bufferUneWriter);
		Float une6_4 = eval6_4FromSubtitle(bufferSubtitleIn,bufferUneWriter);
		Float une6_7 = eval6_7FromSubtitle(bufferSubtitleIn, bufferUneWriter);
		try {
			bufferUneWriter.write(new StringBuilder(rttmIn.getCanonicalPath()).append(" ")
					.append("une45=").append(une4_5==null ? "NA" : une4_5.toString()).append(" ")
					.append("une64=").append(une6_4==null ? "NA" : une6_4.toString()).append(" ")
					.append("une67=").append(une6_7==null ? "NA" : une6_7.toString())
					.toString());
			bufferUneWriter.newLine();
			return 0;
		} catch (IOException e) {
			e.printStackTrace();
			return 1;
		}		
		
	}
	
	
	public Float eval4_5FromSubtitle(BufferedReader buffer, BufferedWriter bufferUneWriter) {
		int timedLinesCount = 0, textLinesCount = 0, noContentLinesCount=0,  totalLinesCount=0;
		String line = null;
        int veces1Hablante = 0;
        int veces2Hablantes = 0;
        try{
            do {       
                line = buffer.readLine();
                totalLinesCount++;
                if (line!=null && StringUtils.isNotBlank(line) && !WEBVTT.equals(line.trim()) && !line.trim().matches("^\\d{1,}$")){ 
                	//  * no hemos llegado al final del fichero
                	//  * la línea no es vacía
                	//  * No contiene únicamente WEBVTT como ocurre con los .vtt
                	//  * No es un indice numérico de la entrada de subtítulo como ocurre con los .srt
                	line = line.trim();
                	Matcher rangeTimeMatcher = rangeTimePattern.matcher(line);
                	Matcher speakerMatcher = speakerPattern.matcher(line);                	
                	Matcher sentenceWithoutSpeakerMatcher = sentenceWithoutSpeakerLabeledPattern.matcher(line);
                	Matcher twoSpeakers_4_5_Matcher = twoSpeakersInOneLine_4_5_Pattern.matcher(line);
                    if (rangeTimeMatcher.matches()){
                    	if (!notFirstIntervalTime) {
                    		notFirstIntervalTime = Boolean.TRUE;
                    	}
                    	timedLinesCount++;
                    	System.out.println("Range time line is matched: "+line);		                    	
                    	evalNewInterval(rangeTimeMatcher.group(1), rangeTimeMatcher.group(3), totalLinesCount);
                    	if (getSpeakerFound().equals(SpeakersInInterval.One))
                    			veces1Hablante ++;
                    	setSpeakerFound(SpeakersInInterval.None);
                    }else if (twoSpeakers_4_5_Matcher.matches()) {
                    	firstSpeaker = true;
                    	if (getSpeakerFound().equals(SpeakersInInterval.One) || getSpeakerFound().equals(SpeakersInInterval.None)) {
	                    	textLinesCount++;
	                    	String speaker1TwoSpeakers = twoSpeakers_4_5_Matcher.group(1); 
	                    	String speaker2TwoSpeakers = twoSpeakers_4_5_Matcher.group(2);
	                    	System.out.println("Encontrados en la misma linea de texto "+ textLinesCount+ " dos hablantes: "+ speaker1TwoSpeakers+ " y " + speaker2TwoSpeakers );
	                    	veces2Hablantes ++;
	                    	setSpeakerFound(SpeakersInInterval.More);
                    	}
                    }else if (speakerMatcher.matches()) {
                    	textLinesCount++;
                    	firstSpeaker = true;
            	    	String speakerLine = speakerMatcher.group();
            	    	previousSpeaker = currentSpeaker;
            	    	String speaker = speakerLine.substring(1, speakerLine.indexOf(")")).split("-")[0];
            	    	if (getSpeakerFound().equals(SpeakersInInterval.None)) {
            	    		setSpeakerFound(SpeakersInInterval.One);
            	    	}else if (getSpeakerFound().equals(SpeakersInInterval.One)) {
            	    		if (!getLastIntervalIsShort())	
            	    			veces2Hablantes ++;
            	    		setSpeakerFound(SpeakersInInterval.More);
            	    		System.out.println("Encontrados en el mismo intervalo de tiempo, no demasiado corto,  "+ textLinesCount+ " dos hablantes: "+ previousSpeaker+ " y " + currentSpeaker );
            	    	}
                    }else if (sentenceWithoutSpeakerMatcher.matches()) {
                    	textLinesCount++;
            	    	if (getSpeakerFound().equals(SpeakersInInterval.None)) {
            	    		setSpeakerFound(SpeakersInInterval.One);
            	    	}
                    	if (!firstSpeaker) { //En el primer Speech, como no sabemos quien habla, supondremos que es el narrador
                    		firstSpeaker = true;
                    		currentSpeaker = NARRADOR;
                    	}else {                    		
                    		System.out.println("Continua hablando el actual: "+currentSpeaker);	
                    	}
                    }else {
                       	 	noContentLinesCount++;
                       	 	System.out.println("La línea número "+totalLinesCount+ " NO SE HA PODIDO CLASIFICAR. ");
                    }               	
                }else 
               	 	noContentLinesCount++;             	
            }while (line!=null);
            previousSpeaker = currentSpeaker;
       } catch (IOException ioe) {
                System.out.println(ioe.getMessage()); 
                return null;
       }  finally {
           System.out.println("Total de lineas:" + totalLinesCount);
           System.out.println("Lineas de tiempo: "+timedLinesCount);
           System.out.println("Lineas con texto: "+textLinesCount);
           System.out.println("Resto de Lineas: " +noContentLinesCount);
           if (veces1Hablante + veces2Hablantes == 0) {
        	   System.out.println("Métrica 4.5 = NA");
           }else {
        	   System.out.println("Métrica 4.5 = " + veces1Hablante / (veces1Hablante + veces2Hablantes) );
           }
       }
      if (veces1Hablante + veces2Hablantes == 0) 
    	  return null;
      return (float) (veces1Hablante /  (float)(veces1Hablante + veces2Hablantes));	
	}	
	
	public Float eval6_7FromSubtitle(BufferedReader bufferSubtitleIn, BufferedWriter bufferUneWriter) {
		
		
		return null;		
	}

	public Float eval6_4FromSubtitle(BufferedReader bufferSubtitleIn, BufferedWriter bufferUneWriter) {

		return null;
	}

	private Path getDatasetPath() {
		Path datasetNamePath = rttmIn.toPath().getParent().getParent().getFileName();
		if (datasetNamePath.toString().equals("rttm"))
			datasetNamePath = Paths.get(".");
		return datasetNamePath;
	}
	
	@Override
	public Integer call() throws Exception {		
		initializeProperties();
		FileWriter labeledSubtitleWriter = null, uneWriter = null;
		BufferedWriter bufferLabelWriter = null, bufferUneWriter = null;	
		BufferedReader bufferSubtitleIn = null, bufferRttmIn = null; 
		int result = -1;
			try {
				 if (subtitleIn == null && rttmIn == null) {
						System.out.println("No se puede realizar el análisis de cumplimiento de la Norma UNE no se dispone del archivo de subtitulos o no está etiquetado y tampoco disponemos del archivo de la diarización !!");
						return 1;
				 }else if (subtitleIn !=null && BooleanUtils.isTrue(getLabeled())) {
					bufferSubtitleIn = new BufferedReader(new FileReader(subtitleIn), 1000);
					uneWriter =new FileWriter(new File(subtitleIn.getParent(), UNE_METRICS_FILE), APPEND);
					bufferUneWriter = new BufferedWriter(uneWriter, 1000);
					evalUNEFromSubtitle(bufferSubtitleIn, bufferUneWriter);									
				}else if (subtitleIn==null && rttmIn != null) {
					bufferRttmIn = new BufferedReader(new FileReader(rttmIn), 1000);					
					Path subtitleFilePath = Paths.get("./data/subtitles", getDatasetPath().toString(), UNE_METRICS_FILE);					
					uneWriter =new FileWriter(subtitleFilePath.toString(), APPEND);
					bufferUneWriter = new BufferedWriter(uneWriter, 1000);					
					evalUNEFromRttm(bufferRttmIn, bufferUneWriter);
				}else if (subtitleIn!=null && BooleanUtils.isNotTrue(getLabeled()) && rttmIn!=null) {  // ETIQUETAR EL ARCHIVO DE SUBTÍTULOS A PARTIR DEL RTTM					
					filenameWithoutExtension = subtitleIn.getName().substring(0, subtitleIn.getName().lastIndexOf(".")).replace(" ", "_");
					String extension = subtitleIn.getName().substring(subtitleIn.getName().lastIndexOf(".") + 1);
					filenameOut = new File(subtitleIn.getParent(), filenameWithoutExtension + "_labeled." + extension );
					filenameOut.createNewFile();				
					labeledSubtitleWriter = new FileWriter(filenameOut);					
					bufferSubtitleIn = new BufferedReader(new FileReader(subtitleIn), 1000);
					bufferRttmIn = new BufferedReader(new FileReader(rttmIn), 1000);
					bufferLabelWriter = new BufferedWriter(labeledSubtitleWriter, 1000);
					result = labelerHelper.doLabelSubtitle(bufferSubtitleIn, bufferRttmIn, bufferLabelWriter, cumplir6_7);
					if (result == OK) {
						Files.delete(subtitleIn.toPath());						
						bufferLabelWriter.close();
						Files.move(filenameOut.toPath(), subtitleIn.toPath());
						uneWriter =new FileWriter(new File(subtitleIn.getParent(), UNE_METRICS_FILE), APPEND);
						bufferUneWriter = new BufferedWriter(uneWriter, 1000);
						bufferSubtitleIn.close();             // Se cierra el buffer y se abre otra vez sobre el archivo modificado
						bufferSubtitleIn = new BufferedReader(new FileReader(subtitleIn), 1000);
						evalUNEFromSubtitle(bufferSubtitleIn, bufferUneWriter);
					}
				}				
			} catch (FileNotFoundException e1) {
				e1.printStackTrace();
				return 1;
			} catch (IOException e) {
				e.printStackTrace();
				return 1;
			}finally {
				if (bufferUneWriter!=null)
					bufferUneWriter.close();
				if (bufferLabelWriter!=null)
					bufferLabelWriter.close();
				if (bufferSubtitleIn!=null)
					bufferSubtitleIn.close();
				if (bufferRttmIn!=null)
					bufferRttmIn.close();
				if (labeledSubtitleWriter!=null)
					labeledSubtitleWriter.close();
				if (uneWriter!=null)
					uneWriter.close();				
			}
                           
	  return result==OK ? 2 : (result==KO ? 1 : 0);
	}
	
}