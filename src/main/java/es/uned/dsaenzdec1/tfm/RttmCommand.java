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
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

import org.apache.commons.lang3.*;

@Component
@Command(name="Convert Subtitles to RTTM Files", version="1.0", mixinStandardHelpOptions = true, description="Este comando convierte archivos de formato .srt o .vtt a formato .rttm para diarización")
public class RttmCommand implements Callable<Integer> {

    @Option(names = { "-i", "--filenameIn" }, paramLabel = "ARCHIVO DE SUBTITULOS DE ENTRADA", description = "Archivo con subtitulos de entrada")
    File filenameIn;
    
    @Option(names = { "-o", "--filenameOut" }, paramLabel = "ARCHIVO RTTM DE DIARIZACIÓN DE SALIDA", description = "Archivo de salida con la diarización procesada")
    File filenameOut;
        	
    @Option(required = true, names = { "-d", "--delta" }, paramLabel = "PARAM. DE SEGMENTACIÓN", description = "Hiperparametro para segmentación entre speechs")  
	Long deltaHiper;
		
	LocalTime previousLocalTimeBegin;
	LocalTime previousLocalTimeEnd;
	Long previousSpeechMillis;
	LocalTime currentLocalTimeBegin;
	LocalTime currentLocalTimeEnd;	
	Long currentSpeechMillis;
		
	String previousSpeaker;
	String currentSpeaker;	
	boolean firstSpeaker;
	String filenameWithoutExtension;
	
	final static String NARRADOR = "NARRADOR";
	final static String SPEAKER = "SPEAKER";
	final static String CHANNEL_ID = "1";
	final static String NA = "NA";
	final static String SEPARATOR = " ";
	final static String WEBVTT = "WEBVTT";
	
	public enum FormasHablar {
		CANTA("CANTA"),
		GRITA("GRITA"),
		VOZ_RONCA1("VOZ RONCA"),
		VOZ_RONCA2("CON VOZ RONCA"),
		VOZ_BAJA("VOZ BAJA"),
		VOZ_BAJA2("EN VOZ BAJA"),
		VOZ_ALTA("VOZ ALTA"),
		VOZ_ALTA2("EN VOZ ALTA");
				
		String fc;
		public String getFc() {return this.fc;}
		
		FormasHablar(String fc){ this.fc = fc;  }
		
		public static String getValues() {
			 return Arrays.stream(FormasHablar.values()).map(fc -> fc.getFc()).collect(Collectors.joining("|"));
		}
	}
	 
	private void initializeProperties() {
		this.previousLocalTimeBegin = null;
		this.previousLocalTimeEnd = null;
		this.currentLocalTimeBegin = null;
		this.currentLocalTimeEnd = null;
		this.previousSpeaker = null;
		this.currentSpeaker = null;
		this.previousSpeechMillis = 0L;
		this.currentSpeechMillis = 0L;
		this.firstSpeaker = false;
		this.filenameWithoutExtension = null;
	}
	
	
	/**
	 * Produce una linea de texto RTTM en el archivo de salida
	 * @param bufferWriter
	 * @throws IOException
	 */
	private void writeNewRttmLine(BufferedWriter bufferWriter) throws IOException {
		if (this.previousLocalTimeBegin == null) {
			System.out.println("Parece que se ha producido un Overlapping con los speakers: "+previousSpeaker + " y " +currentSpeaker);
			return;
		}
		String turnOnset = previousLocalTimeBegin.toSecondOfDay() 
				+ "." 
				+ ( previousLocalTimeBegin.get(ChronoField.MILLI_OF_SECOND) < 100 
						? "0" + (previousLocalTimeBegin.get(ChronoField.MILLI_OF_SECOND)) 
						: (previousLocalTimeBegin.get(ChronoField.MILLI_OF_SECOND))); 				
		String turnDuration = Math.abs(previousSpeechMillis/1000)  
				+ "." 
				+ ( (previousSpeechMillis%1000) <100 
						? "0" + (previousSpeechMillis%1000) 
						: (previousSpeechMillis%1000));
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
	
	/**
	 * Tras leer una linea del archivo de subtítulos que sigue el patrón (\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})(\\s-->\\s)(\\d{2}:\\d{2}:\\d{2}[,\\.]\\d{3})
	 * Obtenemos las dos marcas de tiempo y la diferencia entre ellas, expresada en milisegundos.  
	 * @param timeStrBegin
	 * @param timeStrEnd
	 * @param bufferWriter
	 * @param lineInt
	 * @throws IOException
	 */
    private void calculateMillisRangeTime(String timeStrBegin, String timeStrEnd, int lineInt) throws IOException {    	
		DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("HH:mm:ss" + timeStrBegin.charAt(8) + "SSS");
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

    /**
     * Una linea de speech del speaker actual o de otro nuevo, en el caso de que haya una ligera pausa de tiempo entre esta linea y la anterior que supera una umbral (dado por un parámetro delta)
     * se entenderá que es una nueva sentencia o en caso contrario, que es parte de la sentencia anterior, lo que hará que creemos una nueva linea RTTM en el primer caso.
     * @param bufferWriter
     * @throws IOException
     */
	private void speechNormalLine(BufferedWriter bufferWriter) throws IOException {
		if (currentLocalTimeBegin!=null && previousLocalTimeEnd!=null && currentLocalTimeBegin.isAfter(previousLocalTimeEnd.plus(deltaHiper, ChronoUnit.MILLIS))) {
			previousSpeaker = currentSpeaker;
			writeNewRttmLine(bufferWriter);
			this.previousLocalTimeBegin = this.currentLocalTimeBegin;
			this.currentLocalTimeBegin = null;
			this.previousLocalTimeEnd = this.currentLocalTimeEnd;
			this.currentLocalTimeEnd = null;
			this.previousSpeechMillis = previousLocalTimeBegin.until(previousLocalTimeEnd, ChronoUnit.MILLIS );
		}else if (currentLocalTimeBegin!=null && previousLocalTimeEnd!=null && 
				(currentLocalTimeBegin.isBefore(previousLocalTimeEnd.plus(deltaHiper, ChronoUnit.MILLIS)) || currentLocalTimeBegin.equals(previousLocalTimeEnd.plus(deltaHiper, ChronoUnit.MILLIS)))) 
		{
			this.previousLocalTimeEnd = this.currentLocalTimeEnd;
			this.currentLocalTimeBegin = null;  
			this.currentLocalTimeEnd = null;	
			this.previousSpeechMillis = previousLocalTimeBegin.until(previousLocalTimeEnd, ChronoUnit.MILLIS );                    			                    		                    			
		}		
	}    
        
	@Override
	public Integer call() throws Exception {
		initializeProperties();
        String line = null;
        FileReader reader = null;
        FileWriter writer = null;
		try {
			reader = new FileReader(filenameIn);
			filenameWithoutExtension = filenameIn.getName().substring(0, filenameIn.getName().lastIndexOf(".")).replace(" ", "_");
			filenameOut = new File(filenameOut.getPath().replace(" ", "_"));
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
        Pattern speakerPattern = Pattern.compile("^\\([A-Z\\s\\-,.\\:]+\\).*$");
        Pattern sentenceWithoutSpeakerLabeledPattern = Pattern.compile("^(?!\\()[A-Za-zÑñ\\sá-úà-ùä-üâ-ûÁ-ÚÀ-ÙÄ-ÜÂ-Û[0-9]\\.,;\\:\\-\\?¿\\!¡\\\"\\'\\`\\*\\+]+$");
        Pattern formasHablarPattern = Pattern.compile("("+FormasHablar.getValues() +")");
        
        int timedLinesCount = 0, textLinesCount = 0, noContentLinesCount=0,  totalLinesCount=0;
        try{
            do { 
                line = buffer.readLine();
                totalLinesCount++;
                if (line!=null && StringUtils.isNotBlank(line) && !line.trim().matches(".*"+WEBVTT+".*") && !line.trim().matches("^\\d{1,}$")){ 
                	//  * no hemos llegado al final del fichero
                	//  * la línea no es vacía
                	//  * No contiene únicamente WEBVTT como ocurre con los .vtt
                	//  * No es un indice numérico de la entrada de subtítulo como ocurre con los .srt
                	line = line.trim();
                	Matcher rangeTimeMatcher = rangeTimePattern.matcher(line);
                	Matcher speakerMatcher = speakerPattern.matcher(line);                	
                	Matcher sentenceWithoutSpeakerMatcher = sentenceWithoutSpeakerLabeledPattern.matcher(line);
                    if (rangeTimeMatcher.matches()){
                    	timedLinesCount++;
                    	System.out.println("Range time line is matched:"+line);		                    	
                    	calculateMillisRangeTime(rangeTimeMatcher.group(1), rangeTimeMatcher.group(3), totalLinesCount);
                    }else if (speakerMatcher.matches()) {
                    	textLinesCount++;
            	    	String speakerLine = speakerMatcher.group();
            	    	String speaker = speakerLine.substring(1, speakerLine.indexOf(")"));
            	    	
            	    	Matcher formasHablarMatcher = formasHablarPattern.matcher(speaker);
            	    	StringBuffer sb = new StringBuffer();
            	    	while (formasHablarMatcher.find()) {
            	    		formasHablarMatcher.appendReplacement(sb, "");
            	    	}
            	    	formasHablarMatcher.appendTail(sb);
            	    	speaker = sb.toString();
            	    	speaker = speaker.replaceAll("[\\,|\\;|\\:|\\-]", "").trim();
                    	
            	    	if (currentSpeaker==null && StringUtils.isBlank(speaker)) {                        	
                        	if (!firstSpeaker) { //En el primer Speech, si no sabemos quien habla, supondremos que es el narrador
                        		firstSpeaker = true;
                        		currentSpeaker = NARRADOR;            	  
                        	}
            	    	}
            	    	if (currentSpeaker==null && StringUtils.isNotBlank(speaker)) {
                           	if (!firstSpeaker) { 
                        		firstSpeaker = true;
                        	}            	    		
                    		System.out.println("Identificado el primer hablante: "+speaker);                    		
                    		currentSpeaker = speaker;
                    	}else if (!currentSpeaker.equals(speaker) && StringUtils.isNotBlank(speaker)) {
                    		System.out.println("Identificado un nuevo hablante: "+speaker);
                    		previousSpeaker = currentSpeaker;
                    		currentSpeaker = speaker;                    			
                			writeNewRttmLine(bufferWriter);
                			this.previousLocalTimeBegin = null;
                			this.previousLocalTimeEnd = null;
                			if (this.currentLocalTimeBegin!=null)
                				this.currentSpeechMillis = currentLocalTimeBegin.until(currentLocalTimeEnd, ChronoUnit.MILLIS );                   			                    			
                    	}
                    	else if (currentSpeaker.equals(speaker) || StringUtils.isBlank(currentSpeaker)) { 
                    		// El segundo caso del IF es cuando la etiqueta dice algo así como (CANTA) o (GRITA) , o sea que el speaker no ha cambiado, sólo su tono de voz 
                    		System.out.println("ERROR..SEGUNDO ETIQUETADO CONSECUTIVO Continua hablando el actual: "+speaker);
                    		speechNormalLine(bufferWriter);
                    	}	                    	
                    }else if (sentenceWithoutSpeakerMatcher.matches()) {
                    	textLinesCount++;
                    	if (!firstSpeaker) { //En el primer Speech, como no sabemos quien habla, supondremos que es el narrador
                    		firstSpeaker = true;
                    		currentSpeaker = NARRADOR;
                    	}else {                    		
                    		System.out.println("Continua hablando el actual: "+currentSpeaker);
                    		speechNormalLine(bufferWriter);	
                    	}
                    }else {
                   	 	noContentLinesCount++;
                   	 	System.out.println("La línea número "+totalLinesCount+ " NO SE HA PODIDO CLASIFICAR. ");
                    }
                 }else 
                	 noContentLinesCount++;
             }while (line!=null);
             previousSpeaker = currentSpeaker;
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
            System.out.println("Total de lineas:" + totalLinesCount);
            System.out.println("Lineas de tiempo: "+timedLinesCount);
            System.out.println("Lineas con texto: "+textLinesCount);
            System.out.println("Resto de Lineas: " +noContentLinesCount);
        }	    
		return 0;
	}		
}