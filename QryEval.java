/*
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.
 */

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * QryEval is a simple application that reads queries from a file,
 * evaluates them against an index, and writes the results to an
 * output file.  This class contains the main method, a method for
 * reading parameter and query files, initialization methods, a simple
 * query parser, a simple query processor, and methods for reporting
 * results.
 * <p>
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * Everything could be done more efficiently and elegantly.
 * <p>
 * The {@link Qry} hierarchy implements query evaluation using a
 * 'document at a time' (DaaT) methodology.  Initially it contains an
 * #OR operator for the unranked Boolean retrieval model and a #SYN
 * (synonym) operator for any retrieval model.  It is easily extended
 * to support additional query operators and retrieval models.  See
 * the {@link Qry} class for details.
 * <p>
 * The {@link RetrievalModel} hierarchy stores parameters and
 * information required by different retrieval models.  Retrieval
 * models that need these parameters (e.g., BM25 and Indri) use them
 * very frequently, so the RetrievalModel class emphasizes fast access.
 * <p>
 * The {@link Idx} hierarchy provides access to information in the
 * Lucene index.  It is intended to be simpler than accessing the
 * Lucene index directly.
 * <p>
 * As the search engine becomes more complex, it becomes useful to
 * have a standard approach to representing documents and scores.
 * The {@link ScoreList} class provides this capability.
 */
public class QryEval {

	//  --------------- Constants and variables ---------------------

	private static final String USAGE =
			"Usage:  java QryEval paramFile\n\n";

	private static final EnglishAnalyzerConfigurable ANALYZER =
			new EnglishAnalyzerConfigurable(Version.LUCENE_43);
	private static final String[] TEXT_FIELDS =
		{ "body", "title", "url", "inlink" };
	private static final int topKResults = 100;
	private static Map<String, String> parameters;
	private static File out;

	// For Pseudo Relevance Feedback
	private static BufferedReader initialRankingInput;
	private static BufferedWriter expansionQueryFile;
	private static boolean outputExtendedQuery = false;
	private static boolean queryFileEmpty = true;
	//  --------------- Methods ---------------------------------------

	/**
	 * @param args The only argument is the parameter file name.
	 * @throws Exception Error accessing the Lucene index.
	 */
	public static void main(String[] args) throws Exception {

		//  This is a timer that you may find useful.  It is used here to
		//  time how long the entire program takes, but you can move it
		//  around to time specific parts of your code.

		Timer timer = new Timer();
		timer.start ();

		//  Check that a parameter file is included, and that the required
		//  parameters are present.  Just store the parameters.  They get
		//  processed later during initialization of different system
		//  components.

		if (args.length < 1) {
			throw new IllegalArgumentException (USAGE);
		}

		parameters = readParameterFile (args[0]);

		//  Configure query lexical processing to match index lexical
		//  processing.  Initialize the index and retrieval model.

		ANALYZER.setLowercase(true);
		ANALYZER.setStopwordRemoval(true);
		ANALYZER.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);

		Idx.initialize (parameters.get ("indexPath"));
		RetrievalModel model = initializeRetrievalModel (parameters);

		//  Perform experiments.
		out = new File(parameters.get("trecEvalOutputPath"));
		out.createNewFile();
		
		// if this is a letor model, train it first
		if(model instanceof RetrievalModelLetor){
			
			String trainingQueries = parameters.get("letor:trainingQueryFile");
			String trainingRels = parameters.get("letor:trainingQrelsFile");
			String trainingFeatures = parameters.get("letor:trainingFeatureVectorsFile");
			System.out.println("Training the letor svm rank model...");
			((RetrievalModelLetor) model).train(trainingQueries, trainingRels, trainingFeatures);
			System.out.println("Finished training.");
		}
		
		//processQueryFile(parameters.get("queryFilePath"), model);

		//  Clean up.
		timer.stop ();
		System.out.println ("Time:  " + timer);
	}

	/**
	 * Allocate the retrieval model and initialize it using parameters
	 * from the parameter file.
	 * @return The initialized retrieval model
	 * @throws IOException Error accessing the Lucene index.
	 */
	private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
			throws IOException {

		RetrievalModel model = null;
		String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

		if (modelString.equals("unrankedboolean")) {
			model = new RetrievalModelUnrankedBoolean();
		}
		else if (modelString.equals("rankedboolean")) {
			model = new RetrievalModelRankedBoolean();
		}
		else if (modelString.equals("bm25")) {
			double k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
			double b = Double.parseDouble(parameters.get("BM25:b"));
			double k_3 = Double.parseDouble(parameters.get("BM25:k_3"));
			model = new RetrievalModelBM25(k_1, b, k_3);
		}
		else if (modelString.equals("indri")) {
			int mu = Integer.parseInt(parameters.get("Indri:mu"));
			double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
			model = new RetrievalModelIndri(mu, lambda);
		}
		else if(modelString.equals("letor")){
			double k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
			double b = Double.parseDouble(parameters.get("BM25:b"));
			double k_3 = Double.parseDouble(parameters.get("BM25:k_3"));
			RetrievalModelBM25 bm25model = new RetrievalModelBM25(k_1, b, k_3);
			int mu = Integer.parseInt(parameters.get("Indri:mu"));
			double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
			RetrievalModelIndri indrimodel = new RetrievalModelIndri(mu, lambda);
			List<Integer> dfeats = new ArrayList<Integer>();
			if(parameters.containsKey("letor:featureDisable")){
				String disabledFeats = parameters.get("letor:featureDisable");
				String[] featNums = disabledFeats.split(",");
				for(String n : featNums)	dfeats.add(Integer.parseInt(n));
			}
			double c = Double.parseDouble(parameters.get("letor:svmRankParamC"));
			String learnPath = parameters.get("letor:svmRankLearnPath");
			String classifyPath = parameters.get("letor:svmRankClassifyPath");
			String modelPath = parameters.get("letor:svmRankModelFile");
			SVMRankModel svm = new SVMRankModel(c, learnPath, classifyPath, modelPath);
			String pagerank = parameters.get("letor:pageRankFile");
			model = new RetrievalModelLetor(bm25model, indrimodel, svm, dfeats, pagerank);
		}
		else {
			throw new IllegalArgumentException
			("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
		}

		return model;
	}

	/**
	 * Return a query tree that corresponds to the query.
	 * 
	 * @param qString
	 *          A string containing a query.
	 * @param qTree
	 *          A query tree
	 * @throws IOException Error accessing the Lucene index.
	 */
	static Qry parseQuery(String queryString, String qId, RetrievalModel model, boolean enableExpansion) throws IOException {

		//  Add a default query operator to every query. This is a tiny
		//  bit of inefficiency, but it allows other code to assume
		//  that the query will return document IDs and scores.

		String defaultOp = model.defaultQrySopName ();
		String qString = defaultOp + "(" + queryString + ")";
		
		/**
		 * Expand query string if feedback is enabled
		 */
		if(enableExpansion && parameters.containsKey("fb") && 
				parameters.get("fb").equals("true")){
			HashMap<String, Double> initialRankingDocScores = new HashMap<String, Double>();
			
			if(parameters.containsKey("fbInitialRankingFile")){
				//read a document ranking in trec_eval input format from the fbInitialRankingFile;
				// Build the initial ranking docid to score map
				int docsToRead = Math.min(topKResults, Integer.parseInt(parameters.get("fbDocs")));
				for(int i=0; i<docsToRead; i++){
					String line = initialRankingInput.readLine();
					String[] tokens = line.split("[ \t]");
					String externalDocId = tokens[2];
					//System.out.println(externalDocId);
					Double docIndriScore = Double.parseDouble(tokens[4]);
					initialRankingDocScores.put(externalDocId, docIndriScore);
				}
				int i=0;
				while(i<(topKResults - docsToRead) && initialRankingInput.readLine()!=null)		i++;
			}
			
			else{
				// use the query to retrieve documents;
				Qry q = parseQuery(queryString, qId, model, false);
				if (q.args.size() == 1) {
					Qry q_0 = q.args.get(0);
					if (q_0 instanceof QrySop)		q = q_0;
				}

				while ((q != null) && parseQueryCleanup(q))
					;

				ScoreList r = new ScoreList ();
				//RetrievalModel expansionModel = new RetrievalModelIndri(1000, 0.7);	
				if (q.args.size () > 0) {		// Ignore empty queries
					q.initialize (model);
					while (q.docIteratorHasMatch (model)) {
						int docid = q.docIteratorGetMatch ();
						double score = ((QrySop) q).getScore (model);
						r.add (docid, score);
						q.docIteratorAdvancePast (docid);
					}
				}
				r.sort();
				int docsToRead = Math.min(topKResults, Integer.parseInt(parameters.get("fbDocs")));
				for (int i = 0; i < docsToRead; i++){
					initialRankingDocScores.put(Idx.getExternalDocid(r.getDocid(i)), r.getDocidScore(i));

				}
			}
			
			String query_expanded = expandQuery(qString, initialRankingDocScores);
			Double originalWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
			Double expansionWeight = 1.0 - originalWeight;
			
			qString = "#WAND(" + originalWeight.toString() + " " + qString 
					+ " " + expansionWeight.toString() + " " + query_expanded + ")";
				
			if(!queryFileEmpty){expansionQueryFile.write("\n");expansionQueryFile.flush();}
			if(outputExtendedQuery){expansionQueryFile.write(qId + ":" + query_expanded);expansionQueryFile.flush();}
			if(queryFileEmpty)		queryFileEmpty = false;
			System.out.println(qId + ":" + query_expanded);
		}
		//System.out.println("final query: " + qString);
		
		//  Simple query tokenization.  Terms like "near-death" are handled later.
		StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
		String token = null;

		//  This is a simple, stack-based parser. These variables record
		//  the parser's state.

		Qry currentOp = null;
		Stack<Qry> opStack = new Stack<Qry>();
		boolean weightExpected = false;
		Stack<Double> weightStack = new Stack<Double>();

		//  Each pass of the loop processes one token. The query operator
		//  on the top of the opStack is also stored in currentOp to
		//  make the code more readable.
		while (tokens.hasMoreTokens()) {

			token = tokens.nextToken();
			if (token.matches("[ ,(\t\n\r]")) {
				continue;
			} else if (token.equals(")")) {	// Finish current query op.

				// If the current query operator is not an argument to another
				// query operator (i.e., the opStack is empty when the current
				// query operator is removed), we're done (assuming correct
				// syntax - see below).

				opStack.pop();

				if (opStack.empty())
					break;

				// Not done yet.  Add the current operator as an argument to
				// the higher-level operator, and shift processing back to the
				// higher-level operator.

				Qry arg = currentOp;
				currentOp = opStack.peek();
				currentOp.appendArg(arg);
				if(currentOp instanceof QrySopWeightedAnd)
						((QrySopWeightedAnd) currentOp).addWeight(weightStack.pop());
				if(currentOp instanceof QrySopWeightedSum)
						((QrySopWeightedSum) currentOp).addWeight(weightStack.pop());
				
				if(currentOp instanceof QrySopWeightedAnd
							|| currentOp instanceof QrySopWeightedSum)
					weightExpected = true;
					
			} else if (token.equalsIgnoreCase("#or")) {
				currentOp = new QrySopOr ();
				currentOp.setDisplayName (token);
				opStack.push(currentOp);
			} else if (token.equalsIgnoreCase("#and")) {
				currentOp = new QrySopAnd ();
				currentOp.setDisplayName (token);
				opStack.push(currentOp);
			} else if (token.equalsIgnoreCase("#wand")) {
				currentOp = new QrySopWeightedAnd ();
				currentOp.setDisplayName (token);
				opStack.push(currentOp);
				weightExpected = true;
			} else if (token.equalsIgnoreCase("#wsum")) {
				currentOp = new QrySopWeightedSum ();
				currentOp.setDisplayName (token);
				opStack.push(currentOp);
				weightExpected = true;
			} else if (token.equalsIgnoreCase("#sum")) {
				currentOp = new QrySopSum ();
				currentOp.setDisplayName (token);
				opStack.push(currentOp);
			} else if (token.equalsIgnoreCase("#syn")) {
				currentOp = new QryIopSyn();
				currentOp.setDisplayName (token);
				opStack.push(currentOp);
			} else if (token.regionMatches(true, 0, "#near", 0, 5)) {
				String[] subTokens = token.split("/");
				int distance = Integer.parseInt(subTokens[1]);
				currentOp = new QryIopNear(distance);
				currentOp.setDisplayName (token);
				opStack.push(currentOp);
			} else if (token.regionMatches(true, 0, "#window", 0, 7)) {
				String[] subTokens = token.split("/");
				int distance = Integer.parseInt(subTokens[1]);
				currentOp = new QryIopWindow(distance);
				currentOp.setDisplayName (token);
				opStack.push(currentOp);
			} else {
				
				if(weightExpected){
					// assert that this token is a weight
					assert isDouble(token);
					// read this weight and add it to the weight stack
					weightStack.push(Double.valueOf(token));
					weightExpected = false;
					continue;
				}
					
				if(currentOp instanceof QrySopWeightedAnd
							|| currentOp instanceof QrySopWeightedSum)
					weightExpected = true;
				
				int delimiter = token.indexOf('.');
				String field = null;
				String term = null;

				if (delimiter < 0) {
					field = "body";
					term = token;
				} else {
					field = token.substring(delimiter + 1).toLowerCase();
					term = token.substring(0, delimiter);
				}

				if ((field.compareTo("url") != 0) &&
						(field.compareTo("keywords") != 0) &&
						(field.compareTo("title") != 0) &&
						(field.compareTo("body") != 0) &&
						(field.compareTo("inlink") != 0)) {
					throw new IllegalArgumentException ("Error: Unknown field " + token);
				}

				//  Lexical processing, stopwords, stemming.  A loop is used
				//  just in case a term (e.g., "near-death") gets tokenized into
				//  multiple terms (e.g., "near" and "death").

				String t[] = tokenizeQuery(term);
				Double weight = 0.0;
				if(currentOp instanceof QrySopWeightedAnd
						|| currentOp instanceof QrySopWeightedSum)
					weight = weightStack.pop();
				// add the same weight for all args if this is a WAND or WSUM
				for (int j = 0; j < t.length; j++) {
					Qry termOp = new QryIopTerm(t [j], field);
					currentOp.appendArg (termOp);
					if(currentOp instanceof QrySopWeightedAnd)
						((QrySopWeightedAnd) currentOp).addWeight(weight);
					else if(currentOp instanceof QrySopWeightedSum)
						((QrySopWeightedSum) currentOp).addWeight(weight);
				}
				
				
			}
		}

		//  A broken structured query can leave unprocessed tokens on the opStack,
		if (tokens.hasMoreTokens()) {
			throw new IllegalArgumentException
			("Error:  Query syntax is incorrect.  " + qString);
		}

		return currentOp;
	}

	/**
	 * Remove degenerate nodes produced during query parsing, for
	 * example #NEAR/1 (of the) that can't possibly match. It would be
	 * better if those nodes weren't produced at all, but that would
	 * require a stronger query parser.
	 */
	static boolean parseQueryCleanup(Qry q) {

		boolean queryChanged = false;
		// Iterate backwards to prevent problems when args are deleted.
		for (int i = q.args.size() - 1; i >= 0; i--) {
			Qry q_i = q.args.get(i);
			// All operators except TERM operators must have arguments.
			// These nodes could never match.
			if ((q_i.args.size() == 0) &&
					(! (q_i instanceof QryIopTerm))) {
				q.removeArg(i);
				queryChanged = true;
			} else 

				// All operators (except SCORE operators) must have 2 or more
				// arguments. This improves efficiency and readability a bit.
				// However, be careful to stay within the same QrySop / QryIop
				// subclass, otherwise the change might cause a syntax error.

				if ((q_i.args.size() == 1) &&
						(! (q_i instanceof QrySopScore))) {

					Qry q_i_0 = q_i.args.get(0);

					if (((q_i instanceof QrySop) && (q_i_0 instanceof QrySop)) ||
							((q_i instanceof QryIop) && (q_i_0 instanceof QryIop))) {
						q.args.set(i, q_i_0);
						queryChanged = true;
					}
				} else

					// Check the subtree.
					if (parseQueryCleanup (q_i))
						queryChanged = true;
		}

		return queryChanged;
	}

	/**
	 * Print a message indicating the amount of memory used. The caller
	 * can indicate whether garbage collection should be performed,
	 * which slows the program but reduces memory usage.
	 * 
	 * @param gc
	 *          If true, run the garbage collector before reporting.
	 */
	public static void printMemoryUsage(boolean gc) {

		Runtime runtime = Runtime.getRuntime();
		if (gc)
			runtime.gc();
		System.out.println("Memory used:  "
				+ ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
	}

	/**
	 * Process one query.
	 * @param qString A string that contains a query.
	 * @param model The retrieval model determines how matching and scoring is done.
	 * @return Search results
	 * @throws IOException Error accessing the index
	 */
	static ScoreList processQuery(String qString, String qId, RetrievalModel model)
			throws IOException {

		boolean expansion = parameters.containsKey("fb") && parameters.get("fb").equals("true") ? true : false; 
		Qry q = parseQuery(qString, qId, model, expansion);

		// Optimize the query.  Remove query operators (except SCORE
		// operators) that have only 1 argument. This improves efficiency
		// and readability a bit.

		if (q.args.size() == 1) {
			Qry q_0 = q.args.get(0);

			if (q_0 instanceof QrySop) {
				q = q_0;
			}
		}

		while ((q != null) && parseQueryCleanup(q))
			;

		// Show the query that is evaluated

		//System.out.println("    --> " + q);

		if (q != null) {

			ScoreList r = new ScoreList ();

			if (q.args.size () > 0) {		// Ignore empty queries

				q.initialize (model);
				
				while (q.docIteratorHasMatch (model)) {
					int docid = q.docIteratorGetMatch ();
					double score = ((QrySop) q).getScore (model);
					r.add (docid, score);
					q.docIteratorAdvancePast (docid);
				}
				
			}

			return r;
		} else	return null;
	}

	/**
	 * Process one query according to LETOR.
	 * @param qString A string that contains a query.
	 * @param model The retrieval model determines how matching and scoring is done.
	 * @return Search results
	 * @throws IOException Error accessing the index
	 */
	static ScoreList processQueryLetor(String qString, String qId, RetrievalModel model)
			throws IOException {
		boolean expansion = parameters.containsKey("fb") && parameters.get("fb").equals("true") ? true : false; 
		Qry q = parseQuery(qString, qId, model, expansion);

		// Optimize the query.  Remove query operators (except SCORE
		// operators) that have only 1 argument. This improves efficiency
		// and readability a bit.

		if (q.args.size() == 1) {
			Qry q_0 = q.args.get(0);

			if (q_0 instanceof QrySop) {
				q = q_0;
			}
		}

		while ((q != null) && parseQueryCleanup(q))
			;

		// Show the query that is evaluated

		//System.out.println("    --> " + q);

		if (q != null) {

			ScoreList r = new ScoreList ();

			if (q.args.size () > 0) {		// Ignore empty queries

				q.initialize (model);
				
				while (q.docIteratorHasMatch (model)) {
					int docid = q.docIteratorGetMatch ();
					double score = ((QrySop) q).getScore (model);
					r.add (docid, score);
					q.docIteratorAdvancePast (docid);
				}
				
			}

			return r;
		} else	return null;

	}
	
	/**
	 * Process the query file.
	 * @param queryFilePath
	 * @param model
	 * @throws IOException Error accessing the Lucene index.
	 */
	static void processQueryFile(String queryFilePath, RetrievalModel model)
					throws IOException {

		BufferedReader input = null;

		try {
			String qLine = null;
			input = new BufferedReader(new FileReader(queryFilePath));
			if(parameters.containsKey("fbInitialRankingFile"))
				initialRankingInput = new BufferedReader(new FileReader(parameters.get("fbInitialRankingFile")));
			if(parameters.containsKey("fbExpansionQueryFile")){
				outputExtendedQuery = true;
				File expansionQueries = new File(parameters.get("fbExpansionQueryFile"));
				expansionQueries.createNewFile();
				expansionQueryFile = new BufferedWriter(new FileWriter(expansionQueries, true));
			}
			//  Each pass of the loop processes one query.
			while ((qLine = input.readLine()) != null) {
				int d = qLine.indexOf(':');

				if (d < 0) {
					throw new IllegalArgumentException
					("Syntax error:  Missing ':' in query line.");
				}

				//printMemoryUsage(false);

				String qid = qLine.substring(0, d);
				String query = qLine.substring(d + 1);

				//System.out.println("Query " + qLine);

				ScoreList r = null;

				if(model instanceof RetrievalModelLetor)
					r = processQueryLetor(query, qid, model);
				else
					r = processQuery(query, qid, model);

				if (r != null) {
					r.sort();
					printResults(qid, r);
					System.out.println();
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			input.close();
			if(expansionQueryFile != null)	expansionQueryFile.close();
		}
	}

	
	/**
	 * Expand the query according to the Indri query expansion algorithm
	 * @param query
	 * 			Original query
	 * @param initialRankingDocScores
	 * 			Indri scores for the documents returned in initial ranking
	 * @return
	 * 			Expanded version of the query
	 */
	private static String expandQuery(String query, Map<String, Double> initialRankingDocScores) {
		// Map of candidate expansion terms to scores
		HashMap<String, Double> candidateTerms = new HashMap<String, Double>();
		HashMap<String, Long> corpusTermFrequencies = new HashMap<String, Long>();
		
		// collect all candidate terms first
		for(String id: initialRankingDocScores.keySet()){
			try {
				int internalDocId = Idx.getInternalDocid(id);
				TermVector forwardIndex = new TermVector(internalDocId, "body");
				int uniqueStems = forwardIndex.stemsLength();
				for(int i=1; i<uniqueStems; i++){
					String candidateTerm = forwardIndex.stemString(i);
					Long freq = forwardIndex.totalStemFreq(i);
					if(candidateTerm.contains(".") || candidateTerm.contains(","))		continue;
					candidateTerms.put(candidateTerm, 0.0);
					corpusTermFrequencies.put(candidateTerm, freq);
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// compute scores for the candidate terms
		Set<String> tempSet = null;
		for(String id: initialRankingDocScores.keySet()){
			try {
				tempSet = new HashSet(candidateTerms.keySet());
				
				int internalDocId = Idx.getInternalDocid(id);
				Long corpusLen = Idx.getSumOfFieldLengths("body");
				Double mu = Double.parseDouble(parameters.get("fbMu"));
				TermVector forwardIndex = new TermVector(internalDocId, "body");
				int uniqueStems = forwardIndex.stemsLength();
				Integer docLen = forwardIndex.positionsLength();
				
				// compute scores for terms with non-zero tf
				for(int i=1; i<uniqueStems; i++){
				//for(String candidateTerm: candidateTerms.keySet()){
					String candidateTerm = forwardIndex.stemString(i);
					if(!tempSet.contains(candidateTerm))	continue;			// don't double count
					tempSet.remove(candidateTerm);
					Double indriScore = initialRankingDocScores.get(id);
					Long ctf = forwardIndex.totalStemFreq(i);
					Integer tf = forwardIndex.stemFreq(i);
					Double tGivenC = (double) ctf / (double) corpusLen;
					Double tGivenD = ((double) tf + (mu * tGivenC)) / ((double) docLen + mu); 
					Double idf = Math.log(1.0 / tGivenC);
					Double score = tGivenD * indriScore * idf;
					candidateTerms.put(candidateTerm, candidateTerms.get(candidateTerm) + score);
					
				}
				
				// compute scores for terms with zero tf
				for(String candidateTerm: tempSet){
					Double indriScore = initialRankingDocScores.get(id);
					Long ctf = corpusTermFrequencies.get(candidateTerm);
					Integer tf = 0;
					Double tGivenC = (double) ctf / (double) corpusLen;
					Double tGivenD = ((double) tf + (mu * tGivenC)) / ((double) docLen + mu); 
					Double idf = Math.log(1.0 / tGivenC);
					Double score = tGivenD * indriScore * idf;
					candidateTerms.put(candidateTerm, candidateTerms.get(candidateTerm) + score);
					
				}
				tempSet.clear();
				
			} catch (Exception e) {
				System.out.println("Could not read internal doc id or corpus length from the index");
				e.printStackTrace();
			}
		}
			
		// Sort candidate terms by score
		int numExpansionTerms = Integer.parseInt(parameters.get("fbTerms"));
		PriorityQueue<TermScorePair> termScorepairs = new PriorityQueue<TermScorePair>(numExpansionTerms,
				new TermScoreComparator());
		for(String term: candidateTerms.keySet())
			termScorepairs.add(new TermScorePair(term, candidateTerms.get(term)));

		Stack<TermScorePair> topPairs = new Stack<TermScorePair>();
		for(int i=0; i<numExpansionTerms; i++)	topPairs.add(termScorepairs.poll());

		String expandedQuery = "#WAND( ";
		for(int i=0; i<numExpansionTerms; i++){
			TermScorePair tsp = topPairs.pop();
			expandedQuery += String.format( "%.4f", tsp.score ) + " " + tsp.term + " "; 
		}
		expandedQuery += ")";
		
		return expandedQuery;
	}

	/**
	 * Print the query results.
	 * 
	 * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
	 * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
	 * 
	 * QueryID Q0 DocID Rank Score RunID
	 * 
	 * @param queryName
	 *          Original query.
	 * @param result
	 *          A list of document ids and scores
	 * @throws IOException Error accessing the Lucene index.
	 */
	static void printResults(String queryName, ScoreList result) throws IOException {

		BufferedWriter bw = new BufferedWriter(new FileWriter(out, true));
		if (result.size() < 1) {
			//bw.write("\tNo results.\n");
			bw.write(queryName + "\tQ0\tdummy\t1\t0\trun-1\n");
		} else {
			int numResults = Math.min(topKResults, result.size());
			for (int i = 0; i < numResults; i++) {
				bw.write(queryName + "\tQ0\t" + Idx.getExternalDocid(result.getDocid(i)) + "\t"
						+ (i+1) + "\t" + result.getDocidScore(i) + "\trun-2\n");
			}
		}
		bw.close();
	}

	/**
	 * Read the specified parameter file, and confirm that the required
	 * parameters are present.  The parameters are returned in a
	 * HashMap.  The caller (or its minions) are responsible for
	 * processing them.
	 * @return The parameters, in <key, value> format.
	 */
	private static Map<String, String> readParameterFile (String parameterFileName)
			throws IOException {
		Map<String, String> parameters = new HashMap<String, String>();
		File parameterFile = new File (parameterFileName);
		if (! parameterFile.canRead ()) {
			throw new IllegalArgumentException
			("Can't read " + parameterFileName);
		}
		Scanner scan = new Scanner(parameterFile);
		String line = null;
		do {
			line = scan.nextLine();
			String[] pair = line.split ("=");
			parameters.put(pair[0].trim(), pair[1].trim());
		} while (scan.hasNext());

		scan.close();

		if (! (parameters.containsKey ("indexPath") &&
				parameters.containsKey ("queryFilePath") &&
				parameters.containsKey ("trecEvalOutputPath") &&
				parameters.containsKey ("retrievalAlgorithm"))) {
			throw new IllegalArgumentException
			("Required parameters were missing from the parameter file.");
		}

		return parameters;
	}

	/**
	 * Given a query string, returns the terms one at a time with stopwords
	 * removed and the terms stemmed using the Krovetz stemmer.
	 * 
	 * Use this method to process raw query terms.
	 * 
	 * @param query
	 *          String containing query
	 * @return Array of query tokens
	 * @throws IOException Error accessing the Lucene index.
	 */
	static String[] tokenizeQuery(String query) throws IOException {

		TokenStreamComponents comp =
				ANALYZER.createComponents("dummy", new StringReader(query));
		TokenStream tokenStream = comp.getTokenStream();

		CharTermAttribute charTermAttribute =
				tokenStream.addAttribute(CharTermAttribute.class);
		tokenStream.reset();

		List<String> tokens = new ArrayList<String>();

		while (tokenStream.incrementToken()) {
			String term = charTermAttribute.toString();
			tokens.add(term);
		}

		return tokens.toArray (new String[tokens.size()]);
	}
	
	 private static boolean isDouble(String str) {
	        try {
	            Double.parseDouble(str);
	            return true;
	        } catch (NumberFormatException e) {
	            return false;
	        }
	    }
	 
	 private static class TermScoreComparator implements Comparator<TermScorePair>{

		@Override
		public int compare(TermScorePair arg0, TermScorePair arg1) {
			Double difference = arg1.score - arg0.score;
			if (difference < 0.0)
				return -1;
			else if (difference > 0.0)
				return 1;
			else
				return 0;
		}
		 
	 }

}