package analyzer.databaseRunners;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.text.DecimalFormat;

import analyzer.Utilities.Utilities;
import analyzer.variantInfo.Variant;

public class rpsBlastRunner {

    String tempfaaPath;
    String tempoutPath;
    String eval;

    
    public rpsBlastRunner(String outputPath, String eval) throws IOException{
        this.tempfaaPath = outputPath + "temp.faa";
        this.tempoutPath = outputPath + "temp.out";
        this.eval = eval;

        }
    
    public void runRPSBlast(Variant var) throws IOException {
    	
    	//System.out.println(var.toString());
    	
    	if (var.getCDSList().size() == 1) {
            //System.out.println("rpsblast:query");
    		buildRPSQuery(var, 0); 	
        	try {
                //System.out.println("rpsblast:command");
    			runRPSBlastCommand();
    		} catch (Exception e) {
    			e.printStackTrace();
    		}
            //System.out.println("rpsblast:results");
        	extractRPSBlastResults(var, 0);
    	} else if (var.getCDSList().size() != 0){
    		for (int i = 0; i < var.getCDSList().size(); ++i) {
                //System.out.println("rpsblast:query");
    			buildRPSQuery(var, i); 	
            	try {
                    //System.out.println("rpsblast:command");
        			runRPSBlastCommand();
        		} catch (Exception e) {
        			e.printStackTrace();
        		}
                //System.out.println("rpsblast:results");
            	extractRPSBlastResults(var, i);
    		}
    	}
    	
    	
    }

    public void buildRPSQuery(Variant var, Integer num) throws IOException {
    
    	FileWriter tempFAA = new FileWriter(tempfaaPath);
    	StringBuilder fasta = new StringBuilder('>' + var.getChr() + ':' + var.getPos() + '\n' +
    											var.getCDSList().get(num).getOriginalProtein().substring(0, var.getCDSList().get(num).getOriginalProtein().length() - 1) //take off *
    											+ '\n');
    	tempFAA.write(fasta.toString());
    	tempFAA.close();
    }
    
    public void runRPSBlastCommand() throws Exception {
    	//System.out.println(Utilities.GREEN+"Running rpsblast to find Conserved Domains"+ Utilities.RESET);
    	try {
    		String[] call = new String[]{"rpsblast", "-query", tempfaaPath, "-db", "Cdd",
    									 "-out", tempoutPath, "-evalue", eval, "-outfmt", "6 sseqid qstart qend length evalue stitle"};
    		
    		ProcessBuilder pb = new ProcessBuilder(call);

    		Process p = pb.start();
    		p.waitFor();
    		//System.out.println(Utilities.getProcessOutput(p));
    		String error = Utilities.getProcessError(p);
    		p.destroy();
    		
    		Files.deleteIfExists(new File(tempfaaPath).toPath());
    		
    		if(!error.isEmpty()){
                throw new Exception("rpsblast threw the following error: " + error);
            }
    	} catch (IOException e) {
    		e.printStackTrace();
    	} catch (InterruptedException e) {
    		e.printStackTrace();
    	}
    }
    
    public void extractRPSBlastResults(Variant var, Integer num) throws IOException {
    	String thisLine = null;
    	
    	BufferedReader rpsResults = new BufferedReader(new FileReader(new File(tempoutPath)));
    	while ((thisLine = rpsResults.readLine()) != null) {
    		
    		//gnl|CDD|306940	419  	526 	108 	2e-07	pfam00567, TUDOR

    		String[] splitLine = thisLine.split("\t");
    		Integer cddStart = Integer.parseInt(splitLine[1]);
    		Integer cddEnd = Integer.parseInt(splitLine[2]);
    		Double percentLost;
            Double position = Math.ceil(var.WithinGenePos.get(num) / 3.0); // convert to the protein position
            
    		DecimalFormat df = new DecimalFormat("#.##");
			df.setRoundingMode(RoundingMode.CEILING);
			
    		if (cddStart >= position) { // starts after the lost splice site
    			percentLost = 100.0;

    		} else if (cddEnd >= position) { // variant within this domain
    			Double totalDomainLength = (double) (cddEnd - cddStart + 1);
    			Double lostAmount = (double) (cddEnd - position + 1);
    			percentLost = lostAmount / totalDomainLength * 100;
    			

    		} else {
    			percentLost = 0.0;
    		}

    		
    		if (!percentLost.equals(0.0)) {
    			
    			StringBuilder outCD = new StringBuilder();
        		outCD.append(var.getCDSList().get(num).getTransName() + "//" + 
        					 splitLine[0] + "//" + // gnl|CDD|306940
        					 df.format(percentLost) + "//" + // percentLost
        					 splitLine[4] + "//" + // e-val for match
        					 splitLine[5] + "//"
        				);
        		var.ConservedDomains.add(outCD.toString());
    		}
    		
         }  
    	
    	
		Files.deleteIfExists(new File(tempoutPath).toPath());
    	
    	rpsResults.close();
    
    	
    	/* ROUNDING
    	 * System.out.println("cddStart: " + cddStart);
                System.out.println("cddEnd: " + cddEnd);
                System.out.println();
                System.out.println("withinGenePos: " + position);
                System.out.println();
                System.out.println("PercentLost: " + df.format(percentLost));
                
    	 * DecimalFormat df = new DecimalFormat("#.####");
			df.setRoundingMode(RoundingMode.CEILING);
			for (Number n : Arrays.asList(12, 123.12345, 0.23, 0.1, 2341234.212431324)) {
    		Double d = n.doubleValue();
    		System.out.println(df.format(d));
			}
    	 */
    }
    
}