package es.uned.dsaenzdec1.tfm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import es.uned.dsaenzdec1.tfm.helper.LabelerHelper;
import es.uned.dsaenzdec1.tfm.helper.LabelerHelper.ParsedRttmLine;
import lombok.Getter;
import lombok.Setter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name="Evalúa diarización según norma UNE", version="1.0", mixinStandardHelpOptions = true, description="Evalúa los puntos 4.5, 6.4, 6.7 de la norma UNE 153010:2012 asignando a cada archivo"
		+ " y a cada punto un valor entre 0(peor) y 1(mejor), genera un archivo CSV con los resultados ")
public class EvalUneCommand implements Callable<Integer> {


	@Option(names = { "-is", "--input-subtitle" }, paramLabel = "ARCHIVO DE SUBTITULOS EVALUABLE", defaultValue=Option.NULL_VALUE, description = "Archivo con subtitulos a evaluar según UNE")
    File subtitleIn;
	
	@Option(names = { "-ir", "--input-rttm" }, paramLabel = "ARCHIVO RTTM DE APOYO", defaultValue=Option.NULL_VALUE, description = "Archivo Rich Time de apoyo a la evaluación de un subtítulo")
    File rttmIn;
	
	//@Option(names = { "-lab", "--labeled" }, paramLabel = "ARCHIVO DE SUBTITULOS ETIQUETADO ?", description = "Valor Booleano (True/False) que nos indica si el archivo de subtítulos ya está etiquetado ")
	//@Getter
	//Boolean labeled;
	
	@Option(names = { "-6.7", "--une_6.7" }, paramLabel = "SE ETIQUETA CON ABREVIATURAS ?", defaultValue = "true", description = "Valor Booleano (True/False) que nos indica si en caso de tener que etiquetar el archivo de subtítulos, hay que cumplir la norma de usar abreviaturas a partir de la segunda aparición de un hablante.")
	Boolean cumplir6_7;	
	
	final private static Pattern rangeTimePattern = Pattern.compile("(\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})(\\s-->\\s)(\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})");
	final private static Pattern speakerPattern = Pattern.compile("^\\([A-Zaa-z0-9\\s\\-_\\,\\.\\:]+\\).*$");
	final private static Pattern sentenceWithoutSpeakerLabeledPattern = Pattern.compile("^(?!\\()[A-Za-zÑñ\\sá-úà-ùä-üâ-ûÁ-ÚÀ-ÙÄ-ÜÂ-Û[0-9]\\.,;\\:\\-\\?¿\\!¡\\\"\\'\\`\\*\\+]+$");
	final private static Pattern twoSpeakersInOneLinePattern = Pattern.compile("^\\(([A-Za-z0-9\\s\\-_\\,\\.\\:]+)\\).*\\(([A-Za-z0-9\\s\\-_\\,\\.\\:]+)\\).*$");	  
	final private static Integer OK = 0, KO = 1;
	final private static String NARRADOR = "NARRADOR";
	final private static boolean APPEND = Boolean.TRUE;	
	final private static String WEBVTT = "WEBVTT";
		
	final private static long SHORT_INTERVAL = 3000L;		
	
	private enum SpeakersInInterval{
		None, One, More
	}	
	
	private LocalTime currentRttmLocalTimeBegin;
	private LocalTime currentRttmLocalTimeEnd;
	
	private String previousSpeaker;
	private String currentSpeaker;

	private String filenameWithoutExtension;
	private File filenameOut;
	private BufferedReader bufferSubtitleIn;
	private BufferedReader bufferRttmIn;
	
	@Getter @Setter
	private Boolean lastIntervalIsShort;
	@Getter @Setter
	private SpeakersInInterval speakerFound;
	private Boolean notFirstIntervalTime;
	private Boolean firstSpeaker;
	private int npieca = 0, npit = 0;
	
	@Autowired
	private LabelerHelper labelerHelper;
	

	private void initializeProperties() throws FileNotFoundException {
		this.currentRttmLocalTimeBegin = null;
		this.currentRttmLocalTimeEnd = null;
		this.previousSpeaker = null;
		this.currentSpeaker = null;
		this.filenameWithoutExtension = null;
		this.filenameOut = null;		
		this.firstSpeaker = Boolean.FALSE;
		this.lastIntervalIsShort = null;
		this.speakerFound = SpeakersInInterval.None;
		this.notFirstIntervalTime = Boolean.FALSE;
		this.firstSpeaker = Boolean.FALSE;
		if (this.subtitleIn != null)
			this.bufferSubtitleIn = new BufferedReader(new FileReader(subtitleIn), 1000);
		else
			this.bufferSubtitleIn = null;
		if (this.rttmIn != null)
			this.bufferRttmIn = new BufferedReader(new FileReader(rttmIn), 1000);
		else
			this.bufferRttmIn = null;		
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
    		setLastIntervalIsShort(this.currentRttmLocalTimeBegin.until(currentRttmLocalTimeEnd, ChronoUnit.MILLIS ) < SHORT_INTERVAL);
    	}else {
    		throw new RuntimeException(String.format("No hay timestamp de fin en la linea %d", lineInt));
    	}

	}	
    /**
     * Fragmenta un path de archivo Rttm obteniendo el nombre del archivo, el de la carpeta del modelo/modelos utilizados y el nombre de la carpeta de Datasets 
     * @return lista de nombres de carpetas + nombre del archivo
     */
    private List<String> splitRttmPath(){    	
    	String rttmFileName = rttmIn.getName();
    	String modelsFolder = rttmIn.toPath().getParent().getFileName().toString();
    	String datasetFolder = rttmIn.toPath().getParent().getParent().getFileName().toString().equals("rttm") ? "." : rttmIn.toPath().getParent().getParent().getFileName().toString();
    	return Arrays.asList(rttmFileName, modelsFolder, datasetFolder);
    }
	
    /**
     * Realiza la escasa evaluación posible de un punto de la norma UNE a partir unicamente del archivo RTTM  
     * @param bufferUneWriter
     * @return
     */
    public int evalUNEFromRttm(BufferedWriter bufferUneWriter) {
    	Float une4_5 = null, une6_4 = null, une6_7 = null;
    	une4_5 = eval4_5FromRttm();
    	une6_4 = eval6_4FromRttm();
		une6_7 = eval6_7FromRttm(bufferUneWriter);
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
    
	
	private Float eval4_5FromRttm() {
		//throw new NotImplementedException();
		return null;
	}    

	
	private Float eval6_4FromRttm() {
		//throw new NotImplementedException();
		return null;
	}	
	/**
	 * Evalua el punto 6.7 a partir sólo del archivo Rttm
	 * Encuentra la proporción de etiquetado con abreviaturas. Sin embargo, un sistema de diarización no produce per sé 
	 * abreviaturas en las líneas RTTM, (que sepamos) así que el resultado será siempre 0
	 * @param bufferUneWriter
	 * @return
	 */
	private Float eval6_7FromRttm(BufferedWriter bufferUneWriter) {
		float une6_7 = 0.0f;
		try {
			List<ParsedRttmLine> rttmLinesList = labelerHelper.parseAllRttmFile(bufferRttmIn);
			Map<String, Integer> apariciones = new HashMap<>();
			rttmLinesList.stream().forEach(prl -> 
				{
					if (apariciones.containsKey(prl.getSpeaker())) {
						apariciones.put(prl.getSpeaker(), apariciones.get(prl.getSpeaker())+1 );
					}else
						apariciones.put(prl.getSpeaker(), 1);							
				});
			int npit = 0, npieca = 0; 
			// npieca: Número de posteriores intervenciones etiquetadas con abreviatura
			// npit : Número de posteriores intervenciones totales
			for (Entry<String, Integer> entry : apariciones.entrySet()){				
				if (entry.getKey().length()>2) { //No son abreviaturas
					npit += entry.getValue()-1; 
				}else { // Son abreviaturas
					npit += entry.getValue();
					npieca += entry.getValue();
				}
			}
			if (npit == 0)
				return null;
			une6_7 = (float)npieca / (float)npit;						
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Se ha producido una excepción al parsear el archivo Rttm " + rttmIn);
			System.exit(1);
		}
		System.out.println("Métrica 6.7 = " + une6_7 );
		return une6_7;		
	}

	/**
	 * Realiza la evaluación estándar (de los propios subtítulos) de los puntos de la norma UNE.
	 * Escribe un archivo de texto con el resultado de los 3 valores por cada subtítulo.
	 * En caso de que no dispongamos del archivo RTTM, serán sólo 2 puntos de la norma UNE ya que no podremos calcular el punto 6.4.  
	 * @param bufferUneWriter 
	 * @return resultado del proceso.
	 * @throws FileNotFoundException 
	 */
	public int evalUNEFromSubtitle(BufferedWriter bufferUneWriter) throws FileNotFoundException {
		Float une4_5 = eval4_5FromSubtitle();
		initializeProperties();
		Float une6_4 = null;
		if (bufferRttmIn != null) {
			une6_4 = eval6_4FromSubtitle();
			initializeProperties();
		}
		Float une6_7 = eval6_7FromSubtitle();
		try {
			List<String> splittedPath = splitRttmPath();
			String spaceJoinedPath = splittedPath.stream().collect(Collectors.joining(" "));
			bufferUneWriter.write(new StringBuilder(spaceJoinedPath).append(" ")			
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
		
	
	/**
	 * Evalúa el punto 4.5 de la norma: (Líneas distintas por personajes, intervalos de tiempo distintos por cada personaje, 
	 * salvo que estos sean muy cortos).
	 * @return
	 */
	public Float eval4_5FromSubtitle() {
		int timedLinesCount = 0, textLinesCount = 0, noContentLinesCount=0,  totalLinesCount=0;
		String line = null;
        int veces1Hablante = 0;
        int veces2Hablantes = 0;
        try{
            do {       
                line = bufferSubtitleIn.readLine();
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
                	Matcher twoSpeakers_4_5_Matcher = twoSpeakersInOneLinePattern.matcher(line);
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
                    	textLinesCount++;
                    	firstSpeaker = true;
                    	String speaker1TwoSpeakers = twoSpeakers_4_5_Matcher.group(1);
                    	String speaker1 = speaker1TwoSpeakers.substring(1, speaker1TwoSpeakers.indexOf(")"));
                    	String speaker2TwoSpeakers = twoSpeakers_4_5_Matcher.group(2);
                    	String speaker2 = speaker2TwoSpeakers.substring(1, speaker2TwoSpeakers.indexOf(")"));
                    	previousSpeaker = currentSpeaker;
                    	currentSpeaker = speaker2.split("-")[0].toUpperCase();
                    	if (getSpeakerFound().equals(SpeakersInInterval.One) || getSpeakerFound().equals(SpeakersInInterval.None)) {
	                    	System.out.println("Encontrados en la misma linea de texto "+ textLinesCount+ " dos hablantes: "+ speaker1+ " y " + speaker2 );
	                    	if (!getLastIntervalIsShort())
	                    	{
	                    		veces2Hablantes ++;
	                    		setSpeakerFound(SpeakersInInterval.More);
	                    	}else {
	                    		setSpeakerFound(SpeakersInInterval.One);
	                    	}
                    	}
                    }else if (speakerMatcher.matches()) {
                    	textLinesCount++;
                    	firstSpeaker = true;
            	    	String speakerGroup = speakerMatcher.group();
            	    	previousSpeaker = currentSpeaker;
            	    	currentSpeaker = speakerGroup.substring(1, speakerGroup.indexOf(")")).split("-")[0].toUpperCase();
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

	/**
	 * Evalúa el punto 6.4 de la norma: (Utilización de etiquetas para la identificación de personajes, lo interpretamos como que se ha
	 * etiquetado el archivo de subtítulos. ¿que consideramos que significa aquí "etiquetado"). 
	 * Cada racha de lineas RTTM con el mismo hablante es una intervención , contamos el número de etiquetas y obtenemos el porcentaje respecto
	 * a las intervenciones. 
	 * @return
	 */	
	public Float eval6_4FromSubtitle() {
		List<ParsedRttmLine> parsedRttmLinesList = null;
		if (bufferRttmIn==null)
			return null;
		try {
			parsedRttmLinesList = labelerHelper.parseAllRttmFile(bufferRttmIn);		
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}		
		if (CollectionUtils.isEmpty(parsedRttmLinesList)) 
			return null;
		int nit = countInterventions(parsedRttmLinesList); // Número de intervenciones totales
		if (nit == 0)  
			return null;
		int nie = 0;// Número de intervenciones etiquetadas	
		String line = null;
		int labeledLines = 0, remainingLines = 0;
		try {
	        do {       
	            line = bufferSubtitleIn.readLine();
	            if (line!=null && StringUtils.isNotBlank(line) && !WEBVTT.equals(line.trim()) && !line.trim().matches("^\\d{1,}$")){ 
	            	//  * no hemos llegado al final del fichero
	            	//  * la línea no es vacía
	            	//  * No contiene únicamente WEBVTT como ocurre con los .vtt
	            	//  * No es un indice numérico de la entrada de subtítulo como ocurre con los .srt
	            	line = line.trim();
	            	Matcher speakerMatcher = speakerPattern.matcher(line);                	
	            	Matcher twoSpeakers_6_4_Matcher = twoSpeakersInOneLinePattern.matcher(line);	            	
	            	if (twoSpeakers_6_4_Matcher.matches()) {
	            		labeledLines++;
	            		if (!firstSpeaker) 
	            			nie = nie+2;	            		
	            		else {	            			                    	
	                    	String speaker1TwoSpeakers = twoSpeakers_6_4_Matcher.group(1); 
	                    	String speaker1 = speaker1TwoSpeakers.substring(1, speaker1TwoSpeakers.indexOf(")"));	                    		                    		                    		                    	
	                    	currentSpeaker = speaker1.split("-")[0].toUpperCase();
	                    	String currentSpeakerAbbrev = new String("" + currentSpeaker.charAt(0)+ currentSpeaker.charAt(currentSpeaker.length()-1));
	                    	String speaker2TwoSpeakers = twoSpeakers_6_4_Matcher.group(2);
	                    	String speaker2 = speaker2TwoSpeakers.substring(1, speaker2TwoSpeakers.indexOf(")"));
	                		if (((previousSpeaker!=null && !previousSpeaker.equals(currentSpeaker) && !previousSpeaker.equals(currentSpeakerAbbrev))) 
	                				|| previousSpeaker==null) {
	                			nie++; // El primer speaker en esta linea no es igual al anterior
	                		}
	                		nie++; // El segundo speaker en esta linea no puede ser igual al primero
	                    	currentSpeaker = speaker2.split("-")[0].toUpperCase();	            				            			
	                    	previousSpeaker = currentSpeaker;
	            		}
	            		firstSpeaker = true;
	            	}else if (speakerMatcher.matches()) {
	            		labeledLines++;
	            		if (!firstSpeaker) 
	            			nie++;
	            		else {
	            	    	String speakerLine = speakerMatcher.group();	            	    	            	    	            	   
	            	    	currentSpeaker = speakerLine.substring(1, speakerLine.indexOf(")")).split("-")[0].toUpperCase();	            	    	
	                		String currentSpeakerAbbrev = new String("" + currentSpeaker.charAt(0)+ currentSpeaker.charAt(currentSpeaker.length()-1));
	                		if (((previousSpeaker!=null && !previousSpeaker.equals(currentSpeaker) && !previousSpeaker.equals(currentSpeakerAbbrev))) 
	                				|| previousSpeaker==null) {
	                			nie++;
	                		}
	            		}
                		firstSpeaker = true;
            	    	previousSpeaker = currentSpeaker;
	            	}else {
	            		remainingLines++;
	            	}
	            }else {
	            	remainingLines++;
	            }
	        }while (line!=null);
	    } catch (IOException ioe) {
               System.out.println(ioe.getMessage()); 
               return null;
	    }  finally {
          System.out.println("Lineas etiquetadas: "+labeledLines);
          System.out.println("Resto de Lineas: " +remainingLines);
          System.out.println("Métrica 6.4 = " + (float)nie/(float)nit);
      }					
		return (float)nie/(float)nit;
	}
	
	/**
	 * Evalua el punto 6.7 a partir del archivo de subtítulos
	 * Encuentra la proporción de etiquetado con abreviaturas. Sin embargo, un sistema de diarización no produce per sé 
	 * abreviaturas en las líneas RTTM, así que salvo que apliquemos el postprocesado de LabelerHelper, el resultado será siempre 0
	 * @return
	 */
	public Float eval6_7FromSubtitle() {
		String line = null;
		int labeledLines = 0, remainingLines = 0;
		Set<String> speakersSet = new HashSet<>();
		npieca=0;
		npit = 0;
		try {
	        do {       
	            line = bufferSubtitleIn.readLine();
	            if (line!=null && StringUtils.isNotBlank(line) && !WEBVTT.equals(line.trim()) && !line.trim().matches("^\\d{1,}$")){ 
	            	//  * no hemos llegado al final del fichero
	            	//  * la línea no es vacía
	            	//  * No contiene únicamente WEBVTT como ocurre con los .vtt
	            	//  * No es un indice numérico de la entrada de subtítulo como ocurre con los .srt
	            	line = line.trim();
	            	Matcher speakerMatcher = speakerPattern.matcher(line);                	
	            	Matcher twoSpeakers_6_7_Matcher = twoSpeakersInOneLinePattern.matcher(line);
	            	if (twoSpeakers_6_7_Matcher.matches()) {
	            		labeledLines++;
                    	String speaker1TwoSpeakers = twoSpeakers_6_7_Matcher.group(1);                     	
                    	String speaker1 = speaker1TwoSpeakers.substring(1, speaker1TwoSpeakers.indexOf(")")).split("-")[0].toUpperCase();                    	
                    	countLaterInterventionsAbbreviated(speaker1, speakersSet);	            			            	
                    	
                    	String speaker2TwoSpeakers = twoSpeakers_6_7_Matcher.group(2);	         
                    	String speaker2 = speaker2TwoSpeakers.substring(1, speaker2TwoSpeakers.indexOf(")")).split("-")[0].toUpperCase();
                    	countLaterInterventionsAbbreviated(speaker2, speakersSet);	            		
	            	}else if (speakerMatcher.matches()) {
	            		labeledLines++;  
            	    	String speakerGroup = speakerMatcher.group();
            	    	String speaker = speakerGroup.substring(1, speakerGroup.indexOf(")")).split("-")[0].toUpperCase();
            	    	countLaterInterventionsAbbreviated(speaker, speakersSet);            	    	
	            	}else {
	            		remainingLines++;
	            	}
            	}else {
            		remainingLines++;
            	}
	        }while (line!=null);
	         
	    } catch (IOException ioe) {
	           System.out.println(ioe.getMessage()); 
	           return null;
	    }  finally {	      
	      System.out.println("Lineas etiquetadas: "+labeledLines);
	      System.out.println("Resto de Lineas: " +remainingLines);
	      if (npit == 0)
	    	  return null;
	      System.out.println("Métrica 6.7 = " + (float)npieca/(float)npit);
	  }							
		return (float)npieca/(float)npit;		
	}

	/**
	 * Cuenta el número de intervenciones de distintos hablantes
	 * @param parsedRttmLinesList
	 * @return
	 */
	private int countInterventions(List<ParsedRttmLine> parsedRttmLinesList) {
		List<ParsedRttmLine> distinctSpeakers = IntStream.rangeClosed(0, parsedRttmLinesList.size()-1)
                .filter(i -> i==0 || !parsedRttmLinesList.get(i - 1).getSpeaker().equals(parsedRttmLinesList.get(i).getSpeaker()))
                .mapToObj(i -> parsedRttmLinesList.get(i))
                .collect(Collectors.toList());
		return distinctSpeakers.size();
	}
	/**
	 *  Cuenta el número de intervenciones de un hablante posteriores a la primera.
	 * @param speaker
	 * @param speakersSet
	 */
	private void countLaterInterventionsAbbreviated(String speaker, Set<String> speakersSet) {
    	if (speakersSet.contains(speaker)) {
    		if (speaker.length() <= 2) {            	    		
    			npieca++;
    		}
    		npit++;
    	}else {
    		speakersSet.add(speaker);	
    	}	            			            			
	}	
	
	@Override
	public Integer call() throws Exception {		
		initializeProperties();
		FileWriter labeledSubtitleWriter = null, uneWriter = null;
		BufferedWriter bufferLabelWriter = null, bufferUneWriter = null;	 
		int result = -1;
			try {
				 if (subtitleIn == null && rttmIn == null) {  //No se puede hacer nada
						System.out.println("No se puede realizar el análisis de cumplimiento de la Norma UNE no se dispone del archivo de subtitulos o no está etiquetado y tampoco disponemos del archivo de la diarización !!");
						return 1;									
				}else if (subtitleIn==null && rttmIn != null) { // Evaluar lo que se pueda con el RTTM									
					uneWriter =new FileWriter(Paths.get( Application.rttmsPath.toString(), Application.UNE_METRICS_FILE).toString(), APPEND);
					bufferUneWriter = new BufferedWriter(uneWriter, 1000);					
					result = evalUNEFromRttm(bufferUneWriter);
					
				 }else if (subtitleIn !=null && rttmIn == null) { //Evaluación por subtítulos sin previo etiquetado
					 
					
					uneWriter =new FileWriter(Paths.get( Application.rttmsPath.toString(), Application.UNE_METRICS_FILE).toString(), APPEND);
					bufferUneWriter = new BufferedWriter(uneWriter, 1000);
					result = evalUNEFromSubtitle(bufferUneWriter);
					
				}else if (subtitleIn!=null && rttmIn!=null) {  // Etiquetar el archivo de subtítulos a partir del RTTM para evaluarlo después
															
					filenameWithoutExtension = subtitleIn.getName().substring(0, subtitleIn.getName().lastIndexOf(".")).replace(" ", "_");										
					String extension = subtitleIn.getName().substring(subtitleIn.getName().lastIndexOf(".") + 1);
					
					filenameOut = new File(subtitleIn.getParent(), filenameWithoutExtension + "_unlabeled." + extension );
					filenameOut.createNewFile();				
					labeledSubtitleWriter = new FileWriter(filenameOut);					
					bufferLabelWriter = new BufferedWriter(labeledSubtitleWriter, 1000);					
					result = labelerHelper.doUnlabelSubtitle(bufferSubtitleIn, bufferLabelWriter);
					changeLabelUnlabelFile(result, "(des)");
					if (result == OK) {					
						filenameOut = new File(subtitleIn.getParent(), filenameWithoutExtension + "_labeled." + extension );
						filenameOut.createNewFile();				
						labeledSubtitleWriter = new FileWriter(filenameOut);					
						bufferSubtitleIn = new BufferedReader(new FileReader(subtitleIn), 1000);
						bufferRttmIn = new BufferedReader(new FileReader(rttmIn), 1000);
						bufferLabelWriter = new BufferedWriter(labeledSubtitleWriter, 1000);
						result = labelerHelper.doLabelSubtitle(bufferSubtitleIn, bufferRttmIn, bufferLabelWriter, cumplir6_7);
						changeLabelUnlabelFile(result, "");
						if (result == OK) {
							uneWriter =new FileWriter(Paths.get( Application.rttmsPath.toString(), Application.UNE_METRICS_FILE).toString(), APPEND);
							bufferUneWriter = new BufferedWriter(uneWriter, 1000);
							bufferSubtitleIn = new BufferedReader(new FileReader(subtitleIn), 1000);
							bufferRttmIn = new BufferedReader(new FileReader(rttmIn), 1000);
							result = evalUNEFromSubtitle(bufferUneWriter);
						}else {       //result == 1--> KO
							System.out.println(String.format("ALGO HA FALLADO en el etiquetado de %s", subtitleIn.toString()));
							result = evalUNEFromRttm(bufferUneWriter);
						}
					}else {       //result == 1--> KO
						System.out.println(String.format("ALGO HA FALLADO en el (des)etiquetado de %s", subtitleIn.toString()));
						result = evalUNEFromRttm(bufferUneWriter);
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
                           
	  return result;
	}
	
	
	private void changeLabelUnlabelFile(int result, String unlabel) throws IOException {
		if (result == 0) {
			System.out.println(String.format("Se ha realizado el %setiquetado correcto de %s", unlabel, subtitleIn.toString()));
			bufferSubtitleIn.close();
			Files.delete(subtitleIn.toPath());						
			Files.move(filenameOut.toPath(), subtitleIn.toPath());
		}else {       //result == 1--> KO
			System.out.println(String.format("ALGO HA FALLADO en el %setiquetado de %s",  unlabel, subtitleIn.toString()));
		}		
	}	
	
}