/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gate.twitter.expander;

/**
 * An exception to indicate that the Twitter API could not satisfy the request.
 * 
 * This exception stores the HTTP result status code and message. The client
 * can use that information to determine how to react to the problem, e.g.
 * retry later or give up.
 * 
 * @author Johann Petrak
 */
public class TwitterApiException extends RuntimeException {
  public int statusCode;
  public String reasonPhrase;
  public TwitterApiException() {
  }

  public TwitterApiException(String message) {
    super(message);
  }
  
  public TwitterApiException(String message, Throwable cause) {
    super(message, cause);
  }
  
  public TwitterApiException(Throwable e) {
    super(e);
  }
  
}
