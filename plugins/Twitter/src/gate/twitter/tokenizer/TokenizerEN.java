package gate.twitter.tokenizer;

import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.tokeniser.DefaultTokeniser;

import java.net.URL;

@CreoleResource(name = "Twitter Tokenizer (EN)")
public class TokenizerEN extends DefaultTokeniser {

  private static final long serialVersionUID = -8104798447326556796L;

  @Override
  @CreoleParameter(comment="The URL to the rules file", suffixes="rules",
  defaultValue = "resources/tokeniser/twitter+English.jape")
  public void setTransducerGrammarURL(URL url) {
    super.setTransducerGrammarURL(url);
  }
}
