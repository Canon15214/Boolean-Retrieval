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
	private String learnBinaryPath;
	private String classifyBinaryPath;
	private String modelPath;
	
	public SVMRankModel(double C, String learnPath, String classifyPath, String modelPath){
		this.C = C;
		this.learnBinaryPath = learnPath;
		this.classifyBinaryPath = classifyPath;
		this.modelPath = modelPath;
	}

}
