import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.FileNotFoundException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DelayMoviesSubtitles {

	private final static String ADVANCE = "advance";
		private final static String DELAY = "delay";
	 

	    private String composeIntervalLine(Matcher matcher, String seconds, String advanceDelay){
	    	String finalLine = delayTime(matcher, 3, seconds, advanceDelay);
	    	finalLine += matcher.group(4); 
	    	finalLine += delayTime(matcher, 7, seconds, advanceDelay);
	    	finalLine += matcher.group(8);
	    	return finalLine;
	    }
	    
		private String delayTime(Matcher matcher, int j, String secondsStr, String advanceDelay) {
			String instantString= "", timeUnitString ="";
			int seconds = Integer.parseInt(secondsStr);
			boolean minutes_1 = false, horas_1 = false;
			for (int i=j; i>=j-2;i--){
				String group = matcher.group(i);
				int timeUnit = Integer.parseInt(group);
				if (i==j){
					if (advanceDelay.equals(DELAY)){
						if (timeUnit < seconds){
							timeUnit += 60;
							minutes_1 = true;
						}
						timeUnit -= seconds;
					}else{
						timeUnit += seconds;
						if (timeUnit>= 60){
							timeUnit -= 60;
							minutes_1 = true;
						}
					}
					timeUnitString = unitTimeFromIntToString(timeUnit);
					instantString = timeUnitString; 
				}
				if (i==j-1) {
					if (minutes_1){
						if (advanceDelay.equals(DELAY)){
							if (timeUnit==0){
								timeUnit=59;
								horas_1 = true;
							}
							else
								timeUnit -= 1;
						}else{
							if (timeUnit==59){
								timeUnit=0;
								horas_1 = true;
							}
							else
								timeUnit += 1;							
						}
					}
					timeUnitString = unitTimeFromIntToString(timeUnit);
					instantString = timeUnitString + ":"+ instantString;
				}
				if (i==j-2) {
					if (horas_1){
						if (advanceDelay.equals(DELAY))
							timeUnit -=1;
						else
							timeUnit +=1;
					}
					timeUnitString = unitTimeFromIntToString(timeUnit);
					instantString = timeUnitString + ":"+ instantString;
				}
			}
			return instantString;
		}
		
		private String unitTimeFromIntToString(int timeUnit){
			String finalTimeString = "";
			if (timeUnit<10){
				finalTimeString= "0"+ Integer.toString(timeUnit);
			}else{
				finalTimeString= Integer.toString(timeUnit);
			}
			return finalTimeString;
		}
		
		
		/** TODO: Que admita segundos negativos en args[2], con lo que args[3] se ignorará		
			TODO: Nuevos parámetros args[5] y args[6] para delimitar un rango de tiempo en formato HH:MM:SS 
			en el que se aplicará la modificación
		 */ 
		/**		
		 * @doc args[0] : Nombre de archivo de entrada de Subtitulos STRING
		 * @doc args[1] : Nombre de archivo de salida de Subtitulos ( el de entrada ya modificado) STRING
		 * @doc args[2] : Segundos a adelantar o atrasar el archivo INTEGER		 
		 * @doc args[3] : adelantar o retrasar ("advance" o "delay") 
		 * @param args  
		 */
			    public DelayMoviesSubtitles(String[] args) {
			        String filenameIn = args[0];
			        String filenameOut = args[1];
			        String seconds = args[2];
			        String advanceDelay = args[3];
			        String line = null;
			        FileReader reader = null;
			        FileWriter writer = null;
					try {
						reader = new FileReader(filenameIn);
						File file = new File(filenameOut);
						file.createNewFile();				
						writer = new FileWriter(file);
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
			        Pattern pattern1 = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2})(,\\d{3}\\s-->\\s)(\\d{2}):(\\d{2}):(\\d{2})(,\\d{3})");
			        int linesModified =0, linesNotModified=0;
			        try{
			            do { 
			                    line = buffer.readLine();
			                    if (line!=null){
				                    Matcher matcher1 = pattern1.matcher(line);
				                    if (matcher1.matches()){
				                    	System.out.println("line is matched:"+line);
				                    	line = composeIntervalLine(matcher1, seconds, advanceDelay);
				                    	linesModified++;
				                    }else{
				                    	linesNotModified++;
				                    }
			                    	bufferWriter.write(line);
			                    	bufferWriter.newLine();
			                    }

			             }while (line!=null);
			        } catch (IOException ioe) {
			            System.out.println(ioe.getMessage());
			        }  finally {
			            try {
			            	bufferWriter.close();
							buffer.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
			            System.out.println("Lineas modificadas: "+linesModified);
			            System.out.println("Lineas no modificadas: "+linesNotModified);
			        }
			    }
			    
			    /**
			     * @param args the command line arguments
			     */
			    public static void main(String[] args) {
			    	new DelayMoviesSubtitles(args);
			    }		

}