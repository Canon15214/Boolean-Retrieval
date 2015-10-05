import java.util.HashMap;
import java.util.Set;

/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the Indri ranked
 *  retrieval model and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {

  /**
   * Parameters for the model
   */
	
  public int mu;
  public double lambda;
	
  /**
   * Custom constructor
   */
  public RetrievalModelIndri(int mu, double lambda){
	  this.mu = mu;
	  this.lambda = lambda;
  }
  
  public String defaultQrySopName () {
    return new String ("#and");
  }
  
}
