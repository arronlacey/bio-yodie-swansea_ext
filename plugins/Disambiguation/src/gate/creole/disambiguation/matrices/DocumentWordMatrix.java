package gate.creole.disambiguation.matrices;

import java.util.ArrayList;

import edu.ucla.sspace.vector.CompactSparseVector;

public class DocumentWordMatrix {

	public class SparseVector{
		ArrayList<Integer> indices = new ArrayList<Integer>();
		ArrayList<Double> values = new ArrayList<Double>();
		
		public void print(){
			for(int i=0;i<indices.size();i++){
				System.out.println(indices.get(i) + ": " + values.get(i));
			}
		}
	}
	
	//matrix contains an arraylist of document arraylists (containing word bags).
	ArrayList<SparseVector> matrix = new ArrayList<SparseVector>();
	
	//We need to store word->index mapping also.
	ArrayList<String> wordlist = new ArrayList<String>();
	
	//We'll store the document frequencies for reuse where poss.
	int[] documentFrequencies = null;
	
	boolean documentFrequenciesNeedRecalculating = false;
	
	//Given a string, splits on space and makes a word bag.
	//Returns the index for the document, for later retrieval.
	public int addDocument(String string){
		int index = documents(); //Where to put the new one
		SparseVector newDocumentVector = makeDocumentVector(string, true);
		matrix.add(newDocumentVector);
		documentFrequenciesNeedRecalculating = true;
		return index;
	}
	

	public SparseVector tfidf(String document){
		SparseVector contextVector = makeDocumentVector(document, false);
		return tfidf(contextVector);
	}
	
	//Given a word bag vector, tfidf-weight it according
	//to the current matrix and return.
	public SparseVector tfidf(SparseVector documentVector){
		if(documentFrequenciesNeedRecalculating || documentFrequencies==null ||
				wordlist.size()!=documentFrequencies.length){
			//Recalculate the document frequencies
			documentFrequencies = initializeNewArray(wordlist.size());
			for(int i=0;i<matrix.size();i++){ //for each document
				SparseVector document = matrix.get(i);
				for(int index=0;index<document.indices.size();index++){
					Integer wordindex = document.indices.get(index);
					documentFrequencies[wordindex.intValue()]++;
				}
			}
		}
		
		SparseVector weightedDocument = new SparseVector();
		for(int i=0;i<documentVector.indices.size();i++){
			Integer documentIndex = documentVector.indices.get(i);
			Double documentValue = documentVector.values.get(i);
			int documentFrequency = documentFrequencies[documentIndex.intValue()];
			Double tfidf = documentValue/documentFrequency;
			weightedDocument.indices.add(documentIndex);
			weightedDocument.values.add(tfidf);
		}		
		return weightedDocument;
	}
	
	public SparseVector getDocumentVectorFromMatrix(int index){
		return matrix.get(index);
	}
	
	public SparseVector makeDocumentVector(String string, boolean augment){
		String[] words = string.split(" ");
		SparseVector newDocumentVector = new SparseVector();
		for(int i=0;i<words.length;i++){
			int wordindex = wordlist.indexOf(words[i]);
			if(wordindex==-1 && augment){
				wordindex = wordlist.size();
				wordlist.add(words[i]);
			}
			if(wordindex!=-1){
				int indexOfValue = newDocumentVector.indices.indexOf(
						new Integer(wordindex));
				if(indexOfValue!=-1){
					Double val = newDocumentVector.values.get(indexOfValue)+1;
					newDocumentVector.indices.set(indexOfValue, wordindex);
					newDocumentVector.values.set(indexOfValue, val);
				} else {
					Double val = 1.0;
					//Needs to be added sorted--where to put it?
					int pos = newDocumentVector.indices.size();
					for(int j=0;j<newDocumentVector.indices.size();j++){
						int testwordindex = newDocumentVector.indices.get(j);
						if(wordindex<testwordindex){
							pos=j;
							break;
						}
					}
					
					newDocumentVector.indices.add(pos, wordindex);
					newDocumentVector.values.add(pos, val);
				}
			}
		}
		return newDocumentVector;
	}
	
	public double[] getWordVector(String word){
		int index = wordlist.indexOf(word);
		return getWordVector(index);
	}

	public double[] getWordVector(int index){
		double[] wordVector = new double[this.documents()];
		for(int i=0;i<this.documents();i++){
			SparseVector documentVector = matrix.get(i);
			int indexOfValue = documentVector.indices.indexOf(new Integer(i));
			if(indexOfValue==-1){
				wordVector[i]=0.0;
			} else {
				wordVector[i]=documentVector.values.get(indexOfValue);
			}
		}
		return wordVector;
	}
	
	public int documents(){
		return matrix.size();
	}
	
	public int words(){
		return wordlist.size();
	}
	
	public double[] toDoubleArray(ArrayList<Double> arraylist, int size){
		Object[] array = arraylist.toArray();
		double[] returnArray = new double[size];
		for(int i=0;i<size;i++){
			if(i<array.length){
				Double d = (Double)array[i];
				returnArray[i]=d;
			} else {
				returnArray[i]=0;
			}
		}
		return returnArray;
	}
	
	public int[] initializeNewArray(int size){
		int[] newArray = new int[size];
		for(int i=0;i<size;i++){
			newArray[i]=0;
		}
		return newArray;
	}
	
	public void printMatrix(){
		for(int i=0;i<matrix.size();i++){
			SparseVector document = matrix.get(i);
			for(int j=0;j<document.indices.size();j++){
				System.out.println(document.indices.get(j) + ", " 
						+ document.values.get(j));
			}
		}
	}
	
	public void printWordlist(){
		for(int i=0;i<wordlist.size();i++){
			System.out.println(i + ": " + wordlist.get(i));
		}
	}
	
	public void printDFs(){
		if(documentFrequencies!=null){
			for(int i=0;i<documentFrequencies.length;i++){
				System.out.println(i + ": " + documentFrequencies[i]);
			}
		} else {
			System.out.println("Document frequencies is null.");
		}
	}
	
	public CompactSparseVector sparseVectorToCompactSparseVector(SparseVector sv){
		int length = wordlist.size();
		Object[] indicesArray = sv.indices.toArray();
		Object[] valuesArray = sv.values.toArray();
		int[] newIndicesArray = new int[indicesArray.length];
		double[] newValuesArray = new double[valuesArray.length];
		for(int i=0;i<indicesArray.length;i++){
			newIndicesArray[i] = ((Integer)indicesArray[i]).intValue();
			newValuesArray[i] = ((Double)valuesArray[i]).doubleValue();
		}
		return new CompactSparseVector(newIndicesArray, newValuesArray, length);
	}
}
