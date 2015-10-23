import java.util.HashMap;
import java.util.Set;

/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the BM25 ranked
 *  retrieval model and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {

  /**
   * Parameters for the model
   */
  public double k_1;
  public double b;
  public double k_3;
	
  /**
   * Custom constructor
   */
  public RetrievalModelBM25(double k_1, double b, double k_3){
	  this.k_1 = k_1;
	  this.b = b;
	  this.k_3 = k_3;
  }
  
  public String defaultQrySopName () {
    return new String ("#sum");
  }
  
}
