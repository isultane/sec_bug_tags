Author: Yohan Jo
E-mail: yohanj@cs.cmu.edu
Last Modified: Nov 28, 2014


SLDA

Description

The Sentence LDA model assumes one topic for one sentence. Thus, all words in one sentence are derived from one topic.
Cite: Yohan Jo and Alice Oh, Aspect and Sentiment Unification Model for Online Review Analysis, ACM WSDM, 2011

Input Files

- BagOfSentences.txt (required)
- WordList.txt (reqiured)

BagOfSentences.txt
	This file is a word-list representation of the documents in the corpus.
	For each document, word indices are separated by space, and sentences are separated by tab. Each document is written in one line.

WordList.txt
	This file maps words with word indices. Each word is written in one line. It is assumed that the first word in the file has the index 0, the second word index 1, and so on.

Note: 
BagOfSentences.txt is the content of your documents wherein words are replaced by their indices and sentences are separated by tabs.
DocumentList.txt includes the names of documents. You may not need document names in your task, but nevertheless you need this file to run the code successfully. The number of lines in this file and that of BagOfSentences.txt should be the same.


Output Files

All output file names have the form of
	SLDA-T[t]- A[a]-B[b]-I[i]-[variable].csv
where
	t: number of topics
	a: alpha
	b: beta
	i: number of sampling iterations
	variable: inferred variable

Variables
	DTTheta
		Document X Topic Matrix
		Because of the definition of theta, the probabilities of all topics must sum up to 1 for each document.

	Phi
		Word X Topic Matrix
		Because of the definition of Phi, the probabilities of all words must sum up to 1 for each topic.

	ProbWords
		In this file, the top 100 words for each topic are listed in descending order. The number of words to show is currently hard-coded in the source code.



Program Arguments

Example
	-t 30 -i 1000 -to 200 -th 3 -a 0.1 -b 0.001 -d /home/user/input -o /home/user/output

Options
-t
	Number of topics
-i
	Number of sampling iterations
-to
	Sampling intervals for which intermediate output files are generated
	For example, if it is set to 200, intermediate output files are generated every 200 iterations.
-th
	Number of threads to use
	This multi-thread version split documents into threads, but all threads share the same resources. Note that it does not implement synchronization.
-a
	Symmetric alpha value
-b
	Symmetric beta values
-d
	Directory path under which all input files are located
-o
	Directory path under which output files will be written
	If this option is not set, the output directory is assumed to be the same as the input directory.




