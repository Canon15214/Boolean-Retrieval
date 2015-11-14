import java.util.List;

/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the Letor
 *  retrieval model and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelLetor extends RetrievalModel {

  /**
   * Parameters for the model
   */
	
  private RetrievalModelBM25 bm25model;
  private RetrievalModelIndri indrimodel;
  private SVMRankModel svmrankmodel;
  private List<Integer> disabledFeatures;
  private String pageRankFile;
	
  /**
   * Custom constructor
   */
  public RetrievalModelLetor(RetrievalModelBM25 bm25, RetrievalModelIndri indrimodel, 
		  SVMRankModel svmrankmodel, List<Integer> dfeats, String pagerank){
	  this.bm25model = bm25;
	  this.indrimodel = indrimodel;
	  this.svmrankmodel = svmrankmodel;
	  this.disabledFeatures = dfeats;
	  this.pageRankFile = pagerank;
  }
  
  public String defaultQrySopName () {
    return new String ("#and");
  }
  
  /**
   * Train this model using supplied training params
   */
  public void train(String queries, String relevances, String outputFeatures){
	  
  }
  
}
