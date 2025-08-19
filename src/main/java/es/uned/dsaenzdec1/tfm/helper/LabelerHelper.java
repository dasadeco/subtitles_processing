package es.uned.dsaenzdec1.tfm.helper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;

@Component
@Data
public class LabelerHelper {
	
	final static String WEBVTT = "WEBVTT";
	
	private static Pattern rangeTimePattern = Pattern.compile("(\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})(\\s-->\\s)(\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})");
	private static Pattern speakerPattern = Pattern.compile("^\\s*(\\([A-Za-z0-9\\s\\-_\\,\\.\\:]+\\)).*$");
	private static Pattern onlySpeakerPattern = Pattern.compile("\\([A-Za-z0-9\\s\\-_\\,\\.\\:]+\\)");
	private static Pattern sentenceWithoutSpeakerLabeledPattern = Pattern.compile("^(?!\\()[A-Za-zÑñ\\sá-úà-ùä-üâ-ûÁ-ÚÀ-ÙÄ-ÜÂ-Û[0-9]ºª\\.,;\\:\\-\\?¿\\!¡\\\"\\'\\`\\*\\+]+$");
	
	private String currentIntervalSubtitleLine = null;
	private String currentSpeaker = null;
	private String lastSpeaker = null;
	private Matcher lastRangeTimeMatcher = null;
	
	@Getter @Setter
	List<ParsedRttmLine> parsedRttmLinesList = null;
	private Set<String> speakersSet = null;
	
	/**
	 * Hace el etiquetado del archivo de subtítulo a partir del archivo RTTM y con ciertas suposiciones
	 * @param bufferSubtitleIn
	 * @param bufferRttmIn
	 * @param writer
	 * @return Resultado de la operación 0=OK, 1=KO
	 * @throws IOException 
	 */
	public int doLabelSubtitle(BufferedReader bufferSubtitleIn, BufferedReader bufferRttmIn, BufferedWriter bufferWriter, Boolean cumplir6_7) throws IOException{
		speakersSet = new HashSet<String>();
		String subtitleLine = null;				
		int subTimeIntervalLinesCount = 0, subTextLinesCount = 0, subNoContentLinesCount=0, subTotalLinesCount= 0;
		try {
			parseAllRttmFile(bufferRttmIn);
		} catch (IOException e) {
			e.printStackTrace();
			return 1;
		}
		
		try {
			do {
				subtitleLine = bufferSubtitleIn.readLine();
				subTotalLinesCount++;
                if (subtitleLine!=null && StringUtils.isNotBlank(subtitleLine) && !subtitleLine.trim().matches(".*"+WEBVTT+".*") && !subtitleLine.trim().matches("^\\d{1,}$")){ 
                	//  * no hemos llegado al final del fichero
                	//  * la línea no es vacía
                	//  * No contiene únicamente WEBVTT como ocurre con los .vtt
                	//  * No es un indice numérico de la entrada de subtítulo como ocurre con los .srt
                	//subtitleLine = subtitleLine.trim();
                	Matcher rangeTimeMatcher = rangeTimePattern.matcher(subtitleLine);
                	Matcher speakerMatcher = speakerPattern.matcher(subtitleLine);                	
                	Matcher sentenceWithoutSpeakerMatcher = sentenceWithoutSpeakerLabeledPattern.matcher(subtitleLine);
                    if (rangeTimeMatcher.matches()){
                    	subTimeIntervalLinesCount++;
                    	System.out.println("SUBTITLE: Line with RANGE TIME is matched:"+subtitleLine);	
                    	currentIntervalSubtitleLine = subtitleLine;
                    	lastRangeTimeMatcher = rangeTimeMatcher;
                    	bufferWriter.write(subtitleLine);
                    	bufferWriter.newLine();
                    	
	                }else if (sentenceWithoutSpeakerMatcher.matches()) { // La linea del subtitulo no es un intervalo de tiempo
	                	subTextLinesCount++;
	                	System.out.println("SUBTITLE: Text line WITHOUT SPEAKER LABEL is matched:"+subtitleLine);
	                	String newLineSubtitle = subtitleLine;
	                	if (currentIntervalSubtitleLine != null) {
	                		String suitableSpeaker = searchMoreSuitableRttmLine();
	                		lastSpeaker = currentSpeaker;
	                		currentSpeaker = suitableSpeaker.toUpperCase();
	                		String currentSpeakerAbbrev = new String("" + currentSpeaker.charAt(0)+ currentSpeaker.charAt(currentSpeaker.length()-1));
	                		if ((lastSpeaker!=null && (!lastSpeaker.equals(suitableSpeaker)) && !lastSpeaker.equals(currentSpeakerAbbrev)) || lastSpeaker==null) {	                					                				                				                		
		                		if (BooleanUtils.isTrue(cumplir6_7)) {			                		 
			                		 if (speakersSet.contains(currentSpeaker)) { // Para el cumplimiento del punto 6.7 de la UNE 153010, a partir de la segunda aparición 	                			
			                			newLineSubtitle = new StringBuilder("(").append(currentSpeakerAbbrev).append(")").append(subtitleLine).toString();
			                		 }else {
			                			newLineSubtitle = new StringBuilder("(").append(currentSpeaker).append("-").append(currentSpeakerAbbrev).append(")").append(subtitleLine).toString();
			                			speakersSet.add(currentSpeaker);
			                		 }
		                		}else {
		                			newLineSubtitle = new StringBuilder("(").append(currentSpeaker).append(")").append(subtitleLine).toString();
		                			speakersSet.add(currentSpeaker);
		                		}		                		
	                		}
	                		
    	                    bufferWriter.write(newLineSubtitle);
							bufferWriter.newLine();	 
							currentIntervalSubtitleLine = null;
	                	}else {
	                    	bufferWriter.write(subtitleLine);
	                    	bufferWriter.newLine();	                		
	                	}
                    	
                    }else if (speakerMatcher.matches()) {
                    	subTextLinesCount++;
                    	System.out.println("SUBTITLE: Text line WITH SPEAKER LABEL is matched:"+subtitleLine);
  	                    bufferWriter.write(subtitleLine);
						bufferWriter.newLine();
            	    	String speakerLine = speakerMatcher.group();
            	    	String speaker = speakerLine.substring(1, speakerLine.indexOf(")")).split("-")[0];
            	    	/*if (currentSpeaker!=null && currentSpeaker.equals(speaker)) {
            	    		currentIntervalSubtitleLine = null;
            	    	}*/
            	    	lastSpeaker = currentSpeaker;
            	    	currentSpeaker = speaker.toUpperCase();
                    }else {
                    	subNoContentLinesCount++;
                   	 	System.out.println("La línea número "+subTotalLinesCount+ " NO SE HA PODIDO CLASIFICAR. ");
                   	 	bufferWriter.write(subtitleLine);
                   	 	bufferWriter.newLine();                   	 	
                    }                                    
                }else if (subtitleLine!=null){
                	subNoContentLinesCount++;
                	System.out.println("La línea número "+subTotalLinesCount+ " NO SE HA PODIDO CLASIFICAR. ");                	
               	 	bufferWriter.write(subtitleLine);
               	 	bufferWriter.newLine();               	 	
                }								
			}while(subtitleLine != null);
		 subTotalLinesCount--;		 
		}catch (IOException ioe) {
            System.out.println(ioe.getMessage());
            return 1;
        }finally{
        	bufferSubtitleIn.close();
        	bufferRttmIn.close();
        	bufferWriter.close();
            System.out.println("Total de lineas:" + subTotalLinesCount);
            System.out.println("Lineas de tiempo: "+subTimeIntervalLinesCount);
            System.out.println("Lineas con texto: "+subTextLinesCount);
            System.out.println("Resto de Lineas: " +subNoContentLinesCount);        	
        }
        return 0;
	}
	
	
	/**
	 * Elimina el etiquetado previo de un archivo de subtítulso antes de realizar uno nuevo
	 * @param bufferSubtitleIn
	 * @param writer
	 * @return Resultado de la operación 0=OK, 1=KO
	 * @throws IOException 
	 */
	public int doUnlabelSubtitle(BufferedReader bufferSubtitleIn, BufferedWriter bufferWriter) throws IOException{
		String line = null;
		int labeledLines = 0, remainingLines = 0;
		try {
	        do {       
	            line = bufferSubtitleIn.readLine();
	            if (line!=null && StringUtils.isNotBlank(line) && !WEBVTT.equals(line.trim()) && !line.trim().matches("^\\d{1,}$")){ 
	            	//  * no hemos llegado al final del fichero, la línea no es vacía, no contiene únicamente WEBVTT como ocurre con los .vtt
	            	//  * No es un indice numérico de la entrada de subtítulo como ocurre con los .srt
	            	line = line.trim();
	            	Matcher speakerMatcher = onlySpeakerPattern.matcher(line);
	            	
	            	if (speakerMatcher.find()) {
	            		int start = speakerMatcher.toMatchResult().start();
	            		int end = speakerMatcher.toMatchResult().end();
	            		labeledLines++;  
            	    	String newLineSubtitle = line.substring(0, start).concat(line.substring(end));
	                    bufferWriter.write(newLineSubtitle);
						bufferWriter.newLine();
	            	}else {
	            		remainingLines++;
	                    bufferWriter.write(line);
						bufferWriter.newLine();	            		
	            	}
            	}else {
            		remainingLines++;
            		if (line!=null) {
	                    bufferWriter.write(line);
						bufferWriter.newLine();
            		}
            	}
	        }while (line!=null);
	         
	    } catch (Exception exc) {
	           System.out.println(exc.getMessage()); 
	           return 1;
	    }finally {
	    	bufferSubtitleIn.close();
	    	bufferWriter.close();
	    }
    	System.out.println("Lineas etiquetadas: "+labeledLines);
    	System.out.println("Resto de Lineas: " +remainingLines);	      	  
	    return 0;
	}
	
	
	
	/**
	 * Busca la linea del archivo RTTM más similar en el intervalo de tiempo a la actual del archivo de subtítulos.
	 * @param subtitleLine
	 * @param rangeTimeMatcher
	 * @return Speaker
	 */
	private String searchMoreSuitableRttmLine(){
		
    	if ( StringUtils.isNotBlank(lastRangeTimeMatcher.group(1)) && StringUtils.isNotBlank(lastRangeTimeMatcher.group(3))) {
    		DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("HH:mm:ss" + lastRangeTimeMatcher.group(1).charAt(8) + "SSS");
    		LocalTime currentSubtitleLocalTimeBegin = LocalTime.parse(lastRangeTimeMatcher.group(1), dtFormatter);
    		LocalTime currentSubtitleLocalTimeEnd = LocalTime.parse(lastRangeTimeMatcher.group(3), dtFormatter);
    		long millisSubtitleBegin = currentSubtitleLocalTimeBegin.get(ChronoField.MILLI_OF_DAY);
    		long millisSubtitleEnd = currentSubtitleLocalTimeEnd.get(ChronoField.MILLI_OF_DAY);
    		
    		Optional<ParsedRttmLine> suitableRttmLine = parsedRttmLinesList.stream().min( (rttmLine1,rttmLine2) -> 
    			new CustomComparator(rttmLine1).compareTo(rttmLine2, millisSubtitleBegin, millisSubtitleEnd));
    		return suitableRttmLine.isPresent() ? suitableRttmLine.get().getSpeaker() : null;
    	}
		return null;
	}
	
	/**
	 * Clase comparador de las lineas del archivo RTTM para buscar la más adecuada a la línea actual de Subtítulos que debería ser etiquetada 
	 */
	@Value
	class CustomComparator {
		private ParsedRttmLine parsedRttmLine; 
		public CustomComparator(ParsedRttmLine parsedRttmLine) {
			this.parsedRttmLine = parsedRttmLine;
		}

		public int compareTo(ParsedRttmLine other, Long millisSubtitleBegin, Long millisSubtitleEnd) {
			long millisRttmBeginThis = this.getParsedRttmLine().getRttmLocalTimeBegin().get(ChronoField.MILLI_OF_DAY) + 
					this.getParsedRttmLine().getRttmLocalTimeBegin().get(ChronoField.NANO_OF_SECOND)/1000;
			long millisRttmBeginOther = other.getRttmLocalTimeBegin().get(ChronoField.MILLI_OF_DAY) + 
					this.getParsedRttmLine().getRttmLocalTimeBegin().get(ChronoField.NANO_OF_SECOND)/1000;			
			long millisRttmEndThis = this.getParsedRttmLine().getRttmLocalTimeEnd().get(ChronoField.MILLI_OF_DAY) + 
					this.getParsedRttmLine().getRttmLocalTimeEnd().get(ChronoField.NANO_OF_SECOND)/1000;
			long millisRttmEndOther = other.getRttmLocalTimeEnd().get(ChronoField.MILLI_OF_DAY) + 
					this.getParsedRttmLine().getRttmLocalTimeEnd().get(ChronoField.NANO_OF_SECOND)/1000;			
			Long distanceToSubtitleThis = Math.abs(millisRttmBeginThis - millisSubtitleBegin) + Math.abs(millisRttmEndThis - millisSubtitleEnd); 
			Long distanceToSubtitleOther = Math.abs(millisRttmBeginOther - millisSubtitleBegin) + Math.abs(millisRttmEndOther - millisSubtitleEnd);			
			return distanceToSubtitleThis.compareTo(distanceToSubtitleOther);
		}
	}	
	
	/**
	 * Recorre el archivo RTTM una vez y guarda en una lista 
	 * @param bufferRttmIn
	 * @return 
	 * @throws IOException
	 */
	public List<ParsedRttmLine> parseAllRttmFile(BufferedReader bufferRttmIn) throws IOException {
		if (!CollectionUtils.isEmpty(parsedRttmLinesList))
			return parsedRttmLinesList;
		int rttmLinesOK = 0, rttmLinesKO = 0, rttmTotalLinesCount= 0;
		List<ParsedRttmLine> parsedRttmLinesList = new ArrayList<>();
		String lineRttm = null;
		do {
			lineRttm = bufferRttmIn.readLine();
			rttmTotalLinesCount++;
			if (lineRttm!=null && !lineRttm.trim().isEmpty()) { 
				String[] rttmFields =lineRttm.trim().split("\\s+");
				if (rttmFields.length < 8) {
					System.out.println("Linea "+rttmTotalLinesCount+ " incorrecta como RTTM");
					rttmLinesKO++;
				}else {					
					parsedRttmLinesList.add(instantiateObjectRttmLine(rttmFields[3], rttmFields[4], rttmFields[7]));
					rttmLinesOK++;
				}
			}
		}while(lineRttm!=null && !lineRttm.trim().isEmpty() );    		
        System.out.println("Total de lineas RTTM: " + rttmTotalLinesCount);
        System.out.println("Lineas RTTM OK: "+rttmLinesOK);
        System.out.println("Lineas RTTM KO: "+rttmLinesKO);
        setParsedRttmLinesList(parsedRttmLinesList);
        return parsedRttmLinesList;
	}
	
	/**
	 * Una vez parseada una linea RTTM, parsea algunas de sus partes y crea un objeto que represente los datos que nos interesen  
	 * @param beginRttmInterval
	 * @param endRttmInterval
	 * @param speaker
	 * @return
	 */
	public ParsedRttmLine instantiateObjectRttmLine(String beginRttmInterval, String endRttmInterval, String speaker) {
		String[] timeUnitsBegin = beginRttmInterval.split("\\.");
		String[] timeUnitsEnd = endRttmInterval.split("\\.");
		int horas =  Integer.parseInt(timeUnitsBegin[0]) / 3600;
		int interStep = Integer.parseInt(timeUnitsBegin[0]) % 3600;
		int minutos = interStep / 60;
		int segundos = interStep % 60;
		LocalTime currentRttmLocalTimeBegin = LocalTime.of(horas, minutos, segundos, Integer.parseInt(timeUnitsBegin[1])*1000);
		int suma = Integer.parseInt(timeUnitsBegin[0]) + Integer.parseInt(timeUnitsEnd[0]);
		horas = suma / 3600;
		interStep = suma % 3600;		
		minutos = interStep / 60;
		segundos = interStep % 60;		
		int millisSuma = Integer.parseInt(timeUnitsBegin[1]) + Integer.parseInt(timeUnitsEnd[1]);
		segundos += (millisSuma / 1000);
		if (segundos>=60) {
			minutos += 1;
			segundos = segundos - 60;
		}
		int nanos = (millisSuma % 1000)*1000;
		LocalTime currentRttmLocalTimeEnd = LocalTime.of(horas, minutos, segundos, nanos);
		return new ParsedRttmLine(currentRttmLocalTimeBegin, currentRttmLocalTimeEnd, speaker.toUpperCase());
	}
	
	/**
	 * Objeto que representa una línea RTTM ya parseada.
	 */
	@Value
	public class ParsedRttmLine {		
		private LocalTime rttmLocalTimeBegin;
		private LocalTime rttmLocalTimeEnd;
		private String speaker; 		
	}
					
}