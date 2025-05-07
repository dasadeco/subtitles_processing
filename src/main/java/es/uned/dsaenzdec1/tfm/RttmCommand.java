package es.uned.dsaenzdec1.tfm;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.apache.commons.lang3.*;

@Component
@Command(name="Diarization", version="1.0", mixinStandardHelpOptions = true, description="Pequeño programa que inicialmente sirve para avanzar o retrasar subtitulos")
public class RttmCommand implements Callable<Integer> {

    @Option(names = { "-i", "--filenameIn" }, paramLabel = "ARCHIVO DE SUBTITULOS DE ENTRADA", description = "Archivo con subtitulos de entrada")
    File filenameIn;
    
    @Option(names = { "-o", "--filenameOut" }, paramLabel = "ARCHIVO RTTM DE DIARIZACIÓN DE SALIDA", description = "Archivo de salida con la diarización procesada")
    File filenameOut;
    
	Boolean isVtt;    	
	Long tolerance = 50l;
		
	LocalTime previousLocalTimeBegin = null;
	LocalTime previousLocalTimeEnd = null;
	Long previousSpeechMillis = 0l;
	LocalTime currentLocalTimeBegin = null;
	LocalTime currentLocalTimeEnd = null;	
	Long currentSpeechMillis = 0l;
	
	String previousSpeaker = null;
	String currentSpeaker = null;
	
	final static String NARRADOR = "NARRADOR";
	final static String SPEAKER = "SPEAKER";
	final static String CHANNEL_ID = "1";
	final static String NA = "NA";
	final static String SEPARATOR = " ";
	final static String WEBVTT = "WEBVTT";
	
	enum FormasComunicacion {
		CANTA("CANTA"),
		GRITA("GRITA"),
		VOZ_RONCA("VOZ RONCA"),
		VOZ_BAJA("VOZ BAJA"),
		VOZ_ALTA("VOZ ALTA");
		
		String fc;
		FormasComunicacion(String fc){ this.fc = fc;  }
	}
	

	boolean firstSpeaker = false;
	final static boolean SAME= Boolean.TRUE, DIFFERENT= Boolean.FALSE;
	String filenameWithoutExtension; 

	
	private void writeNewRttmLine(BufferedWriter bufferWriter) throws IOException {
		String turnOnset = previousLocalTimeBegin.toSecondOfDay() + "." + previousLocalTimeBegin.get(ChronoField.MILLI_OF_SECOND);
		String turnDuration = Math.abs(previousSpeechMillis/1000) + "." + (previousSpeechMillis%1000);  
		// Consideramos que hay un gap entre ambos speechs, así que ESCRIBIMOS una linea en el archivo RTTM
		StringBuilder newLine = new StringBuilder(SPEAKER).append(SEPARATOR)
				.append(filenameWithoutExtension).append(SEPARATOR)
				.append(CHANNEL_ID).append(SEPARATOR)
				.append(turnOnset).append(SEPARATOR)
				.append(turnDuration).append(SEPARATOR)
				.append(NA).append(SEPARATOR)
				.append(NA).append(SEPARATOR)
				.append(previousSpeaker).append(SEPARATOR)
				.append(NA).append(SEPARATOR)
				.append(NA).append(SEPARATOR);
		bufferWriter.write(newLine.toString());
		bufferWriter.newLine();
	}
	
    private void calculateMillisRangeTime(String timeStrBegin, String timeStrEnd, BufferedWriter bufferWriter, int lineInt) throws IOException {
    	DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    	if ( StringUtils.isNotBlank(timeStrBegin) && StringUtils.isNotBlank(timeStrEnd)) {         	
        	if (this.currentLocalTimeBegin==null) { //Es el primer rango de LocalTimes del hablante
        		this.currentLocalTimeBegin = LocalTime.parse(timeStrBegin, dtFormatter);
        		this.currentLocalTimeEnd = LocalTime.parse(timeStrEnd, dtFormatter);
        		this.currentSpeechMillis = currentLocalTimeBegin.until(currentLocalTimeEnd, ChronoUnit.MILLIS );
        	}else {
        			this.previousLocalTimeBegin = this.currentLocalTimeBegin;  
        			this.previousLocalTimeEnd = this.currentLocalTimeEnd;		
        			this.currentLocalTimeBegin = LocalTime.parse(timeStrBegin, dtFormatter);
        			this.currentLocalTimeEnd = LocalTime.parse(timeStrEnd, dtFormatter);
        			this.previousSpeechMillis = previousLocalTimeBegin.until(previousLocalTimeEnd, ChronoUnit.MILLIS );
        			this.currentSpeechMillis = currentLocalTimeBegin.until(currentLocalTimeEnd, ChronoUnit.MILLIS );
        		}
        	}else {
        		throw new RuntimeException(String.format("No hay timestamp de fin en la linea %d", lineInt));
        	}
	}

        
	@Override
	public Integer call() throws Exception {
        String line = null;
        FileReader reader = null;
        FileWriter writer = null;
		try {
			reader = new FileReader(filenameIn);
			filenameWithoutExtension = filenameIn.getName().substring(0, filenameIn.getName().lastIndexOf("."));
			isVtt = filenameIn.getName().toLowerCase().endsWith(".vtt");
			filenameOut.createNewFile();				
			writer = new FileWriter(filenameOut);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		/*
		 * It depends on the srt file's size
		 */
        BufferedReader buffer = new BufferedReader(reader, 1000);
        BufferedWriter bufferWriter = new BufferedWriter(writer, 1000);
        Pattern rangeTimePattern = Pattern.compile("(\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})(\\s-->\\s)(\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})");
        Pattern speakerPattern = Pattern.compile("^\\([A-Z\\s]+\\).*$");
        Pattern sentenceWithoutSpeakerLabeledPattern = Pattern.compile("^(?!\\()[A-Za-zÑñ\\sáéíóúâêîôû\\.\\,;\\:\\-_¿\\?\\\"\\'\\`\\*\\+]+$"); 
        
        int timedlinesCount = 0, textlinesCount = 0, noContentLinesCount=0,  totallinesCount=0;
        try{
            do { 
                line = buffer.readLine();
                totallinesCount++;
                if (line!=null && StringUtils.isNotBlank(line) && !WEBVTT.equals(line.trim()) && !line.trim().matches("^\\d{1,}$")){ 
                	//  * no hemos llegado al final del fichero
                	//  * la línea no es vacía
                	//  * No contiene únicamente WEBVTT como ocurre con los .vtt
                	//  * No es un indice numérico de la entrada de subtítulo como ocurre con los .srt
                	line = line.trim();
                	Matcher rangeTimeMatcher = rangeTimePattern.matcher(line);
                	Matcher speakerMatcher = speakerPattern.matcher(line);                	
                	Matcher sentenceWithoutSpeakermatcher = sentenceWithoutSpeakerLabeledPattern.matcher(line);
                    if (rangeTimeMatcher.matches()){
                    	System.out.println("Range time line is matched:"+line);		                    	
                    	timedlinesCount++;
                    	calculateMillisRangeTime(rangeTimeMatcher.group(1), rangeTimeMatcher.group(3), bufferWriter, totallinesCount);
                    }else if (speakerMatcher.matches()){
                    	textlinesCount++;
                    	if (!firstSpeaker)	
                    		firstSpeaker = true;
                    	String speakerLine = speakerMatcher.group();
                    	String speaker = speakerLine.substring(1, speakerLine.indexOf(")"));
                    	if (currentSpeaker==null) {
                    		System.out.println("Identificado el primer hablante: "+speaker);
                    		currentSpeaker = speaker;
                    	}else if (!currentSpeaker.equals(speaker)) {
                    		System.out.println("Identificado un nuevo hablante: "+speaker);
                    		previousSpeaker = currentSpeaker;
                    		currentSpeaker = speaker;                    			
                			writeNewRttmLine(bufferWriter);
                			this.previousLocalTimeBegin = null;
                			this.previousLocalTimeEnd = null;
                			this.currentSpeechMillis = currentLocalTimeBegin.until(currentLocalTimeEnd, ChronoUnit.MILLIS );                   			                    			
                    	}
                    	else if (currentSpeaker.equals(speaker)) {
                    		System.out.println("ERROR..SEGUNDO ETIQUETADO CONSECUTIVO Continua hablando el actual: "+speaker);
                    		if (currentLocalTimeBegin!=null && previousLocalTimeEnd!=null && currentLocalTimeBegin.isAfter(previousLocalTimeEnd.plus(tolerance, ChronoUnit.MILLIS))) {	
                    			previousSpeaker = currentSpeaker;
                    			writeNewRttmLine(bufferWriter);
                    			this.previousLocalTimeBegin = this.currentLocalTimeBegin;
                    			this.currentLocalTimeBegin = null;
                    			this.previousLocalTimeEnd = this.currentLocalTimeEnd;
                    			this.currentLocalTimeEnd = null;          
                    			this.previousSpeechMillis = previousLocalTimeBegin.until(previousLocalTimeEnd, ChronoUnit.MILLIS );
                    		}else if (currentLocalTimeBegin!=null && previousLocalTimeEnd!=null && currentLocalTimeBegin.isBefore(previousLocalTimeEnd.plus(tolerance, ChronoUnit.MILLIS))) {                    			  
                    			this.previousLocalTimeEnd = this.currentLocalTimeEnd;
                    			this.currentLocalTimeBegin = null;  
                    			this.currentLocalTimeEnd = null;	
                    			this.previousSpeechMillis = previousLocalTimeBegin.until(previousLocalTimeEnd, ChronoUnit.MILLIS );                   			                    			
                    		}
                    	}	                    	
                    }else if (sentenceWithoutSpeakermatcher.matches()) {
                    	textlinesCount++;
                    	if (!firstSpeaker) { //En el primer Speech, como no sabemos quien habla, supondremos que es el narrador
                    		firstSpeaker = true;
                    		currentSpeaker = NARRADOR;
                    	}else {                    		
                    		System.out.println("Continua hablando el actual: "+currentSpeaker);
                    		if (currentLocalTimeBegin!=null && previousLocalTimeEnd!=null && currentLocalTimeBegin.isAfter(previousLocalTimeEnd.plus(tolerance, ChronoUnit.MILLIS))) {
                    			previousSpeaker = currentSpeaker;
                    			writeNewRttmLine(bufferWriter);
                    			this.previousLocalTimeBegin = this.currentLocalTimeBegin;
                    			this.currentLocalTimeBegin = null;
                    			this.previousLocalTimeEnd = this.currentLocalTimeEnd;
                    			this.currentLocalTimeEnd = null;
                    			this.previousSpeechMillis = previousLocalTimeBegin.until(previousLocalTimeEnd, ChronoUnit.MILLIS );
                    		}else if (currentLocalTimeBegin!=null && previousLocalTimeEnd!=null && currentLocalTimeBegin.isBefore(previousLocalTimeEnd.plus(tolerance, ChronoUnit.MILLIS))) {
                    			this.previousLocalTimeEnd = this.currentLocalTimeEnd;
                    			this.currentLocalTimeBegin = null;  
                    			this.currentLocalTimeEnd = null;	
                    			this.previousSpeechMillis = previousLocalTimeBegin.until(previousLocalTimeEnd, ChronoUnit.MILLIS );                    			                    		                    			
                    		}
                    	}
                    }
                 }else 
                	 noContentLinesCount++;
             }while (line!=null);
             writeNewRttmLine(bufferWriter);  //Faltaba por escribir la última linea
        } catch (IOException ioe) {
            System.out.println(ioe.getMessage());
        }  finally {
            try {
            	bufferWriter.close();
				buffer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
            System.out.println("Total de lineas:" + totallinesCount);
            System.out.println("Lineas de tiempo: "+timedlinesCount);
            System.out.println("Lineas con texto: "+textlinesCount);
            System.out.println("Resto de Lineas: " +noContentLinesCount);
        }	    
		return 0;
	}
		
}