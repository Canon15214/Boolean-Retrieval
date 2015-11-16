import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All rights reserved.
 */

//TODO: Add copyright statements for SVM Rank

/**
 *  An object that stores parameters for the SVM
 *  ranking model.
 */

public class SVMRankModel {
	
	private double C;
	private String learnBinaryPath;			// specified as an absolute path
	private String classifyBinaryPath;		// specified as an absolute path
	private String modelPath;
	
	public SVMRankModel(double C, String learnPath, String classifyPath, String modelPath){
		this.C = C;
		this.learnBinaryPath = learnPath;
		this.classifyBinaryPath = classifyPath;
		this.modelPath = modelPath;
	}
	
	public void trainModel(String featuresOutputFile) throws Exception{
	    try {
	    	Process cmdProc = Runtime.getRuntime().exec(
	    	        new String[] { learnBinaryPath, "-c", String.valueOf(C), featuresOutputFile,
	    	            modelPath });
		    // The stdout/stderr consuming code prevents the OS from running out 
		    // of output buffer space and stalling.

		    // consume stdout and print it out for debugging purposes
		    BufferedReader stdoutReader = new BufferedReader(
		        new InputStreamReader(cmdProc.getInputStream()));
		    // consume stderr and print it for debugging purposes
		    BufferedReader stderrReader = new BufferedReader(
		        new InputStreamReader(cmdProc.getErrorStream()));
		    String line;
			while ((line = stdoutReader.readLine()) != null)		System.out.println(line);
		    while ((line = stderrReader.readLine()) != null)		System.out.println(line);

		    // get the return value from the executable. 0 means success, non-zero 
		    // indicates a problem
		    int retValue = cmdProc.waitFor();
		    if (retValue != 0) {
		      throw new Exception("SVM Rank crashed.");
		    }
	    
	   } catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}

	}
	
	// generate scores for the testing data
	public void score(String testFeaturesFile, String testScoresFile) throws Exception{
	    try {
	    	Process cmdProc = Runtime.getRuntime().exec(
	    	        new String[] { classifyBinaryPath, testFeaturesFile, modelPath, testScoresFile });
		    // The stdout/stderr consuming code prevents the OS from running out 
		    // of output buffer space and stalling.

		    // consume stdout and print it out for debugging purposes
		    BufferedReader stdoutReader = new BufferedReader(
		        new InputStreamReader(cmdProc.getInputStream()));
		    // consume stderr and print it for debugging purposes
		    BufferedReader stderrReader = new BufferedReader(
		        new InputStreamReader(cmdProc.getErrorStream()));
		    String line;
			while ((line = stdoutReader.readLine()) != null)		System.out.println(line);
		    while ((line = stderrReader.readLine()) != null)		System.out.println(line);

		    // get the return value from the executable. 0 means success, non-zero 
		    // indicates a problem
		    int retValue = cmdProc.waitFor();
		    if (retValue != 0) {
		      throw new Exception("SVM Rank crashed.");
		    }
	    
	   } catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		
	}

}
