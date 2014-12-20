package com.jpetrak.gate.java;

/**
 * Common base class for anyone who wants to invoke a script or a PR 
 * generated from a script. 
 * 
 * This class isolates just those parts of a PR which are responsible for
 * invoking the apropriate scripting methods at the right time. 
 * 
 * TODO: this is not implemented yet and only a place holder!
 * The basic problem so far is that invocation of the compiled script class
 * is done via delegation while we may want to do it via inheritance 
 * for scripts that got converted into a PR. One way to do this may be
 * "self-delegation": a script which got converted to a PR will store itself
 * in the field inherited for where to delegate too and since this field
 * is of a type that is also inherited by the PR, it should be possible to 
 * do it. 
 * The problem then is that Java classes can only inherit from one parent.
 * This means that either this class ScriptInvocation must inherit from 
 * JavaScripting or the other way round. This then creates the problem 
 * that some methods in JavaScripting and in a PR have the same name 
 * (execute, cleanup) -- but maybe it is not really a problem if those
 * methods can be called directly in the converted-to-PR case? Since the 
 * base class methods execute and cleanup are overriden by the script, 
 * the correct code is executed (but the PR should not call super.execute()!)
 * Another issue may be that the PR has to inherit from this class so it 
 * cannot inherit from AbstractLanguageAnalyzer or AbstractProcessingResource,
 * so what is implemented there needs to get implemented here.
 * 
 * @author Johann Petrak
 */
public class ScriptInvocation {
  
}
